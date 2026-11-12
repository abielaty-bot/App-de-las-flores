package pe.lasflores.controlinterno.update

import org.junit.Assert.*
import org.junit.Test

class DownloadProgressTest{
    @Test fun measuresActualBytesAndRemaining(){
        val p=DownloadMeter(1000,100).sample(500,1100)
        assertEquals(50,p.percent)
        assertEquals(500L,p.remaining)
        assertEquals(1000L,p.elapsedMillis)
        assertEquals(500.0,p.bytesPerSecond,0.001)
        assertEquals(1L,p.etaSeconds)
    }
    @Test fun resumedBytesAreNotCountedAsNetworkSpeed(){
        val meter=DownloadMeter(1000,0)
        val resumed=meter.sample(800,100,true)
        assertEquals(80,resumed.percent)
        assertNull(resumed.etaSeconds)
        val next=meter.sample(900,1100)
        assertEquals(100.0,next.bytesPerSecond,0.001)
        assertEquals(1L,next.etaSeconds)
        assertEquals(0L,meter.sample(1000,2100).etaSeconds)
    }
    @Test fun resetWhenServerReturnsFullFile(){
        val meter=DownloadMeter(1000,0)
        meter.sample(700,1,true)
        val reset=meter.sample(0,100,true)
        assertEquals(0,reset.percent)
        assertNull(reset.etaSeconds)
    }
    @Test fun rejectsIncorrectRanges(){
        assertTrue(UpdatePolicy.validContentRange("bytes 100-999/1000",100,1000))
        assertFalse(UpdatePolicy.validContentRange("bytes 0-999/1000",100,1000))
        assertFalse(UpdatePolicy.validContentRange("bytes 100-998/1000",100,1000))
        assertFalse(UpdatePolicy.validContentRange("bytes 100-999/2000",100,1000))
        assertFalse(UpdatePolicy.validContentRange(null,100,1000))
        assertFalse(UpdatePolicy.validContentRange("bytes 1000-1000/1000",1000,1000))
    }
    @Test fun trustedRedirectsRequireHttpsAndOfficialHosts(){
        assertTrue(UpdatePolicy.isTrustedResponseUrl("https://release-assets.githubusercontent.com/test?token=a"))
        assertFalse(UpdatePolicy.isTrustedResponseUrl("https://github.com.evil.example/test"))
        assertFalse(UpdatePolicy.isTrustedResponseUrl("http://release-assets.githubusercontent.com/test"))
    }
}
