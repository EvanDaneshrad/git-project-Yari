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
        try {
            testGit();
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

    public static String createBlobs(String filePath) throws IOException {
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
        for (int i = 0; i < filePaths.length; i++) {
            hashes[i] = createBlobs(filePaths[i]);
            // Here for testing, comment in to check the hashes directly.
            System.out.println("File hash for " + filePaths[i] + ": " + hashFile(filePaths[i]));
        }
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

    public static void testGit() throws IOException {
        System.out.println("== Initial set up ==");
        init();
        File testFile = new File("test.txt");
        File helloFile = new File("Hello.txt");
        File testGit = new File("git/objects/wow.txt");
        testGit.createNewFile();
        testFile.createNewFile();
        helloFile.createNewFile();
        System.out.println("Git created: " + checkIfGitExists());
        System.out.println("Trying to create repository again");
        init();
        System.out.println("Does wow.txt still exist? " + testGit.exists());
        FileWriter w = new FileWriter("test.txt");
        FileWriter writer2 = new FileWriter("hello.txt");
        w.write("I am testing this file for hashing.");
        writer2.write("Hello world!");
        w.close();
        writer2.close();
        System.out.println("== Testing fileHash ==");
        System.out.println(hashFile("test.txt"));
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
        writer = new BufferedWriter(new FileWriter("Hello.txt"));
        System.out.println("== CHECKING OBJECTS ==");
        for (int i = 0; i < stagedFiles.length; i++) {
            File fileTest = new File("git/objects/" + hashes[i]);
            System.out.println(
                    "Does the BLOB file for " + stagedFiles[i] + " exist? " + fileTest.exists());
            BufferedReader reader2 = new BufferedReader(new FileReader(fileTest));
            StringBuilder sb = new StringBuilder();
            while (reader2.ready()) {
                sb.append(reader2.readLine() + "\n");
            }
            System.out.println("Contents of BLOB file: " + sb.toString());
            reader2.close();
        }
        BufferedReader br = new BufferedReader(new FileReader("git/index"));
        System.out.println("Current git/index contents:");
        while (br.ready()) {
            System.out.println(br.readLine());
        }
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
        System.out.println("Current git/index contents:");
        while (br.ready()) {
            System.out.println(br.readLine());
        }
        System.out.println("== CHECKING OBJECTS ==");
        for (int i = 0; i < stagedFiles.length; i++) {
            File fileTest = new File("git/objects/" + hashes[i]);
            System.out.println(
                    "Does the BLOB file for " + stagedFiles[i] + " exist? " + fileTest.exists());
            BufferedReader reader2 = new BufferedReader(new FileReader(fileTest));
            StringBuilder sb = new StringBuilder();
            while (reader2.ready()) {
                sb.append(reader2.readLine() + "\n");
            }
            System.out.println("Contents of BLOB file: " + sb.toString());
            reader2.close();
        }

        br.close();
        testGit.delete();
        cleanUp();

    }

    public static boolean checkIfGitExists() throws IOException {
        if (new File("git").mkdir()) {
            return false;
        }
        if (new File("git/objects").mkdir()) {
            return false;
        }
        if (new File("git/index").createNewFile()) {
            return false;
        }
        if (new File("git/HEAD").createNewFile()) {
            return false;
        }
        return true;
    }

    public static void cleanUp() {
        File git = new File("git");
        File gitObjects = new File("git/objects");
        File[] objectFiles = gitObjects.listFiles();
        for (int i = 0; i < objectFiles.length; i++) {
            objectFiles[i].delete();
        }
        File gitIndex = new File("git/index");
        File gitHead = new File("git/HEAD");
        File test = new File("test.txt");
        File hello = new File("Hello.txt");
        gitObjects.delete();
        gitIndex.delete();
        gitHead.delete();
        git.delete();
        test.delete();
        hello.delete();
    }


}
