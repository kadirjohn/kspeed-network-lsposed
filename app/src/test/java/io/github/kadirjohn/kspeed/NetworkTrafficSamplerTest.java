package io.github.kadirjohn.kspeed;

import org.junit.Test;
import java.io.StringReader;
import java.util.*;
import static org.junit.Assert.*;

public class NetworkTrafficSamplerTest {
    static class Fake implements NetworkTrafficSampler.Backend {
        List<NetworkTrafficSampler.Link> links = new ArrayList<>();
        Map<String,long[]> bytes = new HashMap<>();
        boolean cached;
        @Override public List<NetworkTrafficSampler.Link> links() { return links; }
        @Override public Map<String,long[]> freshCounters() { return cached ? null : bytes; }
        @Override public long[] apiCounters(String name) { return bytes.get(name); }
        void link(String name,int kind,boolean vpn) { links.add(new NetworkTrafficSampler.Link(name,kind,vpn)); bytes.put(name,new long[]{0,0}); }
    }
    @Test public void vpnAdvertisingWifiIsNotCountedTwiceAndDuplicateLinksAreDeduplicated() {
        Fake f = new Fake();
        f.link("wlan0",1,false); f.link("tun0",1,true); f.link("wlan0",1,false);
        NetworkTrafficSampler sampler = new NetworkTrafficSampler(f);
        sampler.sample(0,1000);
        f.bytes.put("wlan0",new long[]{1048576,4000});
        f.bytes.put("tun0",new long[]{1048576,4000});
        f.bytes.put("rmnet_ipa0",new long[]{99999999,99999999}); // not a selected internet link
        assertArrayEquals(new double[]{1048576,4000},sampler.sample(0,2000),0.001);
    }
    @Test public void cachedHalfSecondTickKeepsLastRateAndRealIdleStillBecomesZero() {
        Fake f = new Fake(); f.cached=true; f.link("wlan0",1,false);
        NetworkTrafficSampler sampler = new NetworkTrafficSampler(f);
        sampler.sample(0,1000);
        f.bytes.put("wlan0",new long[]{1048576,0});
        assertEquals(1048576,sampler.sample(0,2000)[0],0.001);
        assertEquals(1048576,sampler.sample(0,2500)[0],0.001);
        f.bytes.put("wlan0",new long[]{2097152,0});
        assertEquals(1048576,sampler.sample(0,3000)[0],0.001);
        assertEquals(0,sampler.sample(0,4000)[0],0.001);
    }
    @Test public void freshCountersSupportHalfSecondRefreshAndSourceFiltering() {
        Fake f = new Fake(); f.link("wlan0",1,false); f.link("rmnet_data4",2,false);
        NetworkTrafficSampler sampler = new NetworkTrafficSampler(f);
        sampler.sample(1,1000);
        f.bytes.put("wlan0",new long[]{524288,1024}); f.bytes.put("rmnet_data4",new long[]{9000000,0});
        assertArrayEquals(new double[]{1048576,2048},sampler.sample(1,1500),0.001);
        assertEquals(0,sampler.sample(2,2000)[0],0.001); // new source starts a baseline
    }
    @Test public void switchingInterfacesAndResettingCountersDoNotProduceSpikes() {
        Fake f = new Fake(); f.link("wlan0",1,false);
        NetworkTrafficSampler sampler = new NetworkTrafficSampler(f); sampler.sample(0,1000);
        f.links.clear(); f.link("rmnet_data4",2,false); f.bytes.put("rmnet_data4",new long[]{90000000,0});
        assertEquals(0,sampler.sample(0,2000)[0],0.001);
        f.bytes.put("rmnet_data4",new long[]{100,0});
        assertEquals(0,sampler.sample(0,3000)[0],0.001);
    }
    @Test public void procParserReadsRxAndTxAndSkipsBadRows() throws Exception {
        Map<String,long[]> values=NetworkTrafficSampler.parseCounters(new StringReader(
            "Inter-| Receive | Transmit\n wlan0: 1024 1 0 0 0 0 0 0 2048 1 0 0 0 0 0 0\n tun0: broken row\n"));
        assertArrayEquals(new long[]{1024,2048},values.get("wlan0")); assertFalse(values.containsKey("tun0"));
    }
}
