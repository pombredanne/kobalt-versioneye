package net.thauvin.erik.kobalt.plugin.versioneye

import com.beust.kobalt.*
import org.testng.*
import org.testng.annotations.*

@Test
class UtilsTest {

    @Test
    fun alt() {
        Assert.assertEquals(Utils.alt(false), "", "alt(false")
        Assert.assertEquals(Utils.alt(true), " [FAILED]", "alt(true)")
    }

    @Test
    fun plural() {
        val singular = "foo"
        val plural = "s"
        Assert.assertEquals(Utils.plural(singular, 0, plural), singular, "plural($singular, count:0, $plural)")
        Assert.assertEquals(Utils.plural(singular, 1, plural), singular, "plural($singular, count:1, $plural)")
        Assert.assertEquals(Utils.plural(singular, 2, plural), singular + plural, "plural($singular, count:2, $plural)")

        val text = "vulnerabilit"
        val y = "y"
        val ies = "ies"
        Assert.assertEquals(Utils.plural(text, 0, ies, y), text + y, "plural($text, count:0, $ies, $y)")
        Assert.assertEquals(Utils.plural(text, 1, ies, y), text + y, "plural($text, count:1, $ies, $y)")
        Assert.assertEquals(Utils.plural(text, 2, ies, y), text + ies, "plural($text, count:2, $ies, $y)")
    }

    @Test
    fun redLight() {
        val text = "This is a test"
        Assert.assertEquals(Utils.redLight(text, 1, true, true), AsciiArt.RED + text + AsciiArt.RESET,
                "redLight(${text}, count:1, fail:true, colors:true)")
        Assert.assertEquals(Utils.redLight(text, 1, false, true), AsciiArt.YELLOW + text + AsciiArt.RESET,
                "redLight(${text}, count:1, fail:false, colors:true)")
        Assert.assertEquals(Utils.redLight(text, 0, false, true), AsciiArt.GREEN + text + AsciiArt.RESET,
                "redLight(${text}, count:0, fail:false, colors:true)")
        Assert.assertEquals(Utils.redLight(text, 1, false, false), text,
                "redLight(${text}, count:1, fail:false, colors:false)")
    }
}