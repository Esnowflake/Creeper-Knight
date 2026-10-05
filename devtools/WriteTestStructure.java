import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

public final class WriteTestStructure {
    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[0]); Files.createDirectories(path.getParent());
        try (var out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(path)))) {
            out.writeByte(10); out.writeUTF("");
            out.writeByte(3); out.writeUTF("DataVersion"); out.writeInt(3465);
            out.writeByte(9); out.writeUTF("size"); out.writeByte(3); out.writeInt(3);
            out.writeInt(16); out.writeInt(8); out.writeInt(16);
            out.writeByte(9); out.writeUTF("palette"); out.writeByte(10); out.writeInt(1);
            out.writeByte(8); out.writeUTF("Name"); out.writeUTF("minecraft:air"); out.writeByte(0);
            for (String name : new String[]{"blocks", "entities"}) {
                out.writeByte(9); out.writeUTF(name); out.writeByte(10); out.writeInt(0);
            }
            out.writeByte(0);
        }
    }
}
