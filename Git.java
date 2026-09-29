import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class Git {

    public static void main(String[] args) {
        init();
        try {
            System.out.println("== Testing fileHash ==");
            System.out.println(hashFile("test.txt"));
            System.out.println("== Testing createBlob ==");
            createBlob("test.txt");
            System.out.println("== TESTING STAGING FILES ==");
            BufferedWriter writer = new BufferedWriter(new FileWriter("Hello.txt"));
            writer.write("I have changed this file once.");
            writer.close();
            String[] stagedFiles = {"Hello.txt", "test.txt"};
            String[] hashes = new String[stagedFiles.length];
            for (int i = 0; i < stagedFiles.length; i++) {
                hashes[i] = hashFile(stagedFiles[i]);
            }
            System.out.println("Files are ready to be staged.");
            stageFiles(stagedFiles, hashes);
            BufferedReader br = new BufferedReader(new FileReader("git/index"));
            writer = new BufferedWriter(new FileWriter("Hello.txt"));
            System.out.println("Current first line in git/index is: " + br.readLine());
            br.close();
            writer.write("I have changed this file twice!" + "\n");
            writer.write("Now I have written three.");
            writer.close();
            System.out.println("Staging again!");
            for (int i = 0; i < stagedFiles.length; i++) {
                hashes[i] = hashFile(stagedFiles[i]);
            }
            System.out.println("Files are ready to be staged.");
            stageFiles(stagedFiles, hashes);
            br = new BufferedReader(new FileReader("git/index"));
            System.out.println(
                    "After file change, the new first line in git/index: " + br.readLine());
            br.close();

        } catch (IOException e) {
            System.out.println("File error: " + e.getMessage());
        }

    }

    public static void init() {
        try {
            File gitRoot = new File("git");
            boolean rootExists = !gitRoot.mkdir();
            File gitObjects = new File("git/objects");
            boolean objectsExists = !gitObjects.mkdir();
            File gitIndex = new File("git/index");
            boolean indexExists = !gitIndex.createNewFile();
            File gitHead = new File("git/HEAD");
            boolean headExists = !gitHead.createNewFile();
            if (rootExists && objectsExists && indexExists && headExists) {
                System.out.println("Git Repository already exists.");
            } else {
                System.out.println("Git Repository created.");
            }

        } catch (IOException e) {
            System.out.println("File error: " + e.getMessage());
        }

    }

    public static String hashFile(String filePath) throws IOException {
        Path path = Path.of(filePath);
        if (!Files.isRegularFile(path)) {
            throw new IOException("no such file: " + filePath);
        }
        byte[] fileBytes = Files.readAllBytes(path);
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is not available", e);
        }

        byte[] hash = digest.digest(fileBytes);
        return HexFormat.of().formatHex(hash);
    }

    public static String createBlob(String filePath) throws IOException {
        try {
            String hash = hashFile(filePath);
            // No need to check whether file exists because hashFile checks that
            Path path = Path.of(filePath);
            byte[] fileBytes = Files.readAllBytes(path);
            Path newObject = Path.of("git/objects/" + hash);
            Files.write(newObject, fileBytes);
            return hash;
        } catch (IOException e) {
            System.out.println("File error: " + e.getMessage());
        }
        return "";
    }

    public static void stageFiles(String[] filePaths, String[] hashes) throws IOException {
        if (filePaths.length != hashes.length) {
            throw new IllegalArgumentException(
                    "One or more files doesn't have a corresponding hash");
        }
        BufferedWriter bw = new BufferedWriter(new FileWriter("git/index"));
        for (int i = 0; i < filePaths.length; i++) {
            if (!Files.isRegularFile(Path.of(filePaths[i]))) {
                bw.close();
                throw new IOException("No such file: " + filePaths[i]);
            }
            bw.write(hashes[i] + " git-project-Yari/" + filePaths[i]);
            if (i != filePaths.length - 1) {
                bw.write("\n");
            }
        }
        bw.close();
    }
}
