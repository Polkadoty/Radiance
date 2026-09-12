import com.radiance.client.vertex.PBRVertexFormats;
import com.radiance.client.vertex.PBRVertexFormatElements;

/** Checks the actual compiled Radiance format declarations, without constructing Minecraft or a renderer. */
public final class PbrFormatLayoutTest {
    private PbrFormatLayoutTest() {}
    public static void main(String[] args) {
        var format=PBRVertexFormats.PBR_TRIANGLE;
        assert format.getVertexSize()==128;
        assert format.getOffset(PBRVertexFormatElements.PBR_POS)==0;
        assert format.getOffset(PBRVertexFormatElements.PBR_USE_NORM)==12;
        assert format.getOffset(PBRVertexFormatElements.PBR_NORM)==16;
        assert format.getOffset(PBRVertexFormatElements.PBR_USE_COLOR_LAYER)==28;
        assert format.getOffset(PBRVertexFormatElements.PBR_COLOR_LAYER)==32;
        assert format.getOffset(PBRVertexFormatElements.PBR_USE_TEXTURE)==48;
        assert format.getOffset(PBRVertexFormatElements.PBR_USE_OVERLAY)==52;
        assert format.getOffset(PBRVertexFormatElements.PBR_TEXTURE_UV)==56;
        assert format.getOffset(PBRVertexFormatElements.PBR_OVERLAY_UV)==64;
        assert format.getOffset(PBRVertexFormatElements.PBR_USE_GLINT)==72;
        assert format.getOffset(PBRVertexFormatElements.PBR_TEXTURE_ID)==76;
        assert format.getOffset(PBRVertexFormatElements.PBR_USE_LIGHT)==92;
        assert format.getOffset(PBRVertexFormatElements.PBR_LIGHT_UV)==96;
        assert format.getOffset(PBRVertexFormatElements.PBR_COORDINATE)==104;
        assert format.getOffset(PBRVertexFormatElements.PBR_ALBEDO_EMISSION)==108;
        assert format.getOffset(PBRVertexFormatElements.PBR_POST_BASE)==112;
        System.out.println("PASS: actual compiled Radiance PBR format offsets match the producer; trailing alpha word is124");
    }
}
