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
import java.util.ArrayList;
import java.util.HashMap;

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

    // Bugs to fix:
    // Two files with the same path cannot exist in index
    // Adding to index doesn't clear the file
    // When a file is modified and staged again, replace its existing index entry with the new hash.
    public static void stageFiles(String[] filePaths, String[] hashes) throws IOException {

        String[] relativeFilePaths = changeBareFileNamesToRelativePaths(filePaths);

        // this represents the files that have already been staged
        ArrayList<String> stagedFiles = new ArrayList<String>();

        // copy what's already in index
        BufferedReader indexReader = new BufferedReader(new FileReader("git/index"));

        StringBuilder indexContent = new StringBuilder();


        String line;

        while ((line = indexReader.readLine()) != null) {
            indexContent.append(line);
            indexContent.append("\n");
        }

        indexReader.close();

        for (int i = 0; i < filePaths.length; i++) {
            hashes[i] = createBlobs(filePaths[i]);
            // Here for testing, comment in to check the hashes directly.
            System.out.println("File hash for " + filePaths[i] + ": " + hashFile(filePaths[i]));
        }

        // this HashMap represents the path/hash pair for every file being staged
        HashMap<String, String> filePathHashMap = new HashMap<String, String>();

        for (int i = 0; i < filePaths.length; i++) {
            filePathHashMap.put(relativeFilePaths[i], hashes[i]);
        }

        // assuming indexContent.length() > 0, we need to check each file in filePaths that's also
        // already inside of index to make sure it hasn't been modified
        // To do this, I'll first need to parse indexContent into an ArrayList of String arrays
        // each index in the List corresponds to a path/hash pair
        // the first index in the String array is the file path, the second index is that file's
        // hash
        ArrayList<String[]> entries = parseIndexContent(indexContent);

        // Then, I'll loop through the list and check for any files with contradicting hashes (files
        // that have been modified)

        // loop through entries
        for (int i = 0; i < entries.size(); i++) {
            // check filePathHashMap for entries.get(i)[0]
            // if filePathHashMap doesn't contain this path, continue (meaning that file isn't being
            // staged again)
            if (!filePathHashMap.containsKey(entries.get(i)[1])) {
                continue;
            }

            // if filePathHashMap does contain this path, confirm that entries.get(i)[0] is that
            // same as filePathHashMap.get(entries.get(i)[1])
            // if not delete that entire entry from indexContent, it will be readded with the
            // correct hash later!
            if (!entries.get(i)[0].equals(filePathHashMap.get(entries.get(i)[1]))) {
                // remove entries.get(i) from entries
                entries.remove(i);

                // rewrite indexContent with the updated entries list
                indexContent = updateIndexContent(entries);
            } 
        }

        // delete the last char from indexContent as it will always be a blank line if something has
        // been appended to it

        // if (indexContent.length() > 0) {
        //     indexContent.deleteCharAt(indexContent.length() - 1);
        // }

        if (filePaths.length != hashes.length) {
            throw new IllegalArgumentException(
                    "One or more files doesn't have a corresponding hash");
        }

        BufferedWriter bw = new BufferedWriter(new FileWriter("git/index"));


        System.out.println(indexContent.toString());

        // write in indexContent before staging new files
        bw.write(indexContent.toString());

        for (int i = 0; i < filePaths.length; i++) {
            if (!Files.isRegularFile(Path.of(filePaths[i]))) {
                bw.close();
                throw new IOException("No such file: " + filePaths[i]);
            }

            // before staging the file check to make sure that it hasn't already been staged and
            // that it's not already inside of index
            // if indexContent contains filePaths[i] or filePaths[i] has already been staged
            // continue
            if (!willFileBeStaged(stagedFiles, indexContent.toString(), filePaths[i])) {
                continue;
            } else {
                // only write to index if neither of the above conditions are true
                bw.write(hashes[i] + " " + relativeFilePaths[i]);

                // write in a new line if the next file is gonna be staged
                if (i != filePaths.length - 1) {
                    if (willFileBeStaged(stagedFiles, indexContent.toString(), filePaths[i + 1])) {
                        bw.write("\n");
                    }
                }

                stagedFiles.add(filePaths[i]);
            }

        }

        bw.close();
    }

    // this method determines whether or not a file will be staged
    // it takes in an ArrayList of files that have already been staged and the contents of the index
    // file before staging and the file path
    public static boolean willFileBeStaged(ArrayList<String> stagedFiles, String indexContent,
            String filePath) {
        if (indexContent.contains(filePath)) {
            return false;
        } else if (stagedFiles.contains(filePath)) {
            return false;
        } else {
            return true;
        }
    }

    // this method takes the entries already within index and parses them into: an ArrayList of
    // String arrays
    // each index in the List corresponds to a path/hash pair
    // the first index in the String array is the file's hash, the second index is that file path
    // the helper returns this parsed list
    // it takes in the contents of the index file as a StringBuilder
    public static ArrayList<String[]> parseIndexContent(StringBuilder indexContent) {
        ArrayList<String[]> entries = new ArrayList<String[]>();

        // split indexContent into a String[] where each index is a line from the original
        // indexContent StringBuilder
        String[] indexLines = indexContent.toString().split("\\R");

        // loop through indexLines
        for (int i = 0; i < indexLines.length; i++) {
            // if the current line isn't empty, split it by space, then add that array to entries
            if (!indexLines[i].isEmpty()) {
                String[] entry = indexLines[i].split(" ", 2);
                entries.add(entry);
            }
        }

        return entries;
    }

    // this method is for after an entry is removed from the index file, and after that entry has
    // been removed from the entries list
    // it takes in the entries list
    public static StringBuilder updateIndexContent(ArrayList<String[]> entries) {
        StringBuilder indexContent = new StringBuilder();

        // loop through entries
        for (int i = 0; i < entries.size(); i++) {
            // append the second index of the current entry array to indexContent
            indexContent.append(entries.get(i)[1]);

            // append a space to indexContent
            indexContent.append(" ");

            // append the first index of the current entry array to indexContent
            indexContent.append(entries.get(i)[0]);

            // append a new line to indexContent
            indexContent.append("\n");
        }

        return indexContent;
    }

    // this method turns the bare file name of each input file and converts it into its relative
    // file path
    // e.g before: abc.txt, after: git-project-Yari/abc.txt
    // it takes in a String[] of the bare file names
    public static String[] changeBareFileNamesToRelativePaths(String[] bareFileNames) {

        String[] relativePaths = new String[bareFileNames.length];

        // change each file path to include the relative path of each file
        for (int i = 0; i < bareFileNames.length; i++) {
            StringBuilder relativePath = new StringBuilder();

            relativePath.append("git-project-Yari/");
            relativePath.append(bareFileNames[i]);

            relativePaths[i] = relativePath.toString();
        }

        return relativePaths;
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

        // if wow.txt still exists that means init() didn't create a new repository and the old one
        // is the one we have rn
        System.out.println("Does wow.txt still exist? " + testGit.exists());

        // changing the name of this writer from w to testWriter
        FileWriter testWriter = new FileWriter("test.txt");

        // changing the name of this writer from writer2 to helloWriter
        // changing file path from: "hello.txt" to "Hello.txt"
        FileWriter helloWriter = new FileWriter("Hello.txt");

        testWriter.write("I am testing this file for hashing.");
        helloWriter.write("Hello world!");

        testWriter.close();
        helloWriter.close();

        System.out.println();

        System.out.println("== Testing fileHash ==");

        System.out.println(hashFile("test.txt"));

        System.out.println("Testing fileHash with a file that has multiple lines written into it");

        // writer to rewrite the BLOB of "test.txt" to have multiple lines
        FileWriter testWriter1 = new FileWriter("test.txt");

        testWriter1.write("I am testing this file for hashing.");
        testWriter1.write("\nAdding another line to the BLOB of this file.");

        testWriter1.close();

        System.out.println(hashFile("test.txt"));

        System.out.println();

        System.out.println("== TESTING STAGING FILES ==");

        // changing the name of this writer from: "writer" to: "helloWriter1"
        BufferedWriter helloWriter1 = new BufferedWriter(new FileWriter("Hello.txt"));

        helloWriter1.write("I have changed this file once.");

        helloWriter1.close();

        // Testing two files with identical content but different paths
        // should produce two lines in index but one file in objects/
        File duplicateHelloFile = new File("Hello1.txt");

        duplicateHelloFile.createNewFile();

        FileWriter duplicateHelloFileWriter = new FileWriter("Hello1.txt");

        duplicateHelloFileWriter.write("I have changed this file once.");

        duplicateHelloFileWriter.close();

        // this is a directory only for testing, it will be deleted at the end of testing
        // for now, I'll use it to test that two files with the same content but different paths add
        // two separate entries to index with the same hash but different paths
        File testingDirectory = new File("TestingDirectory");
        testingDirectory.mkdir();

        File differentPathSameContentHello = new File("TestingDirectory/Hello.txt");
        differentPathSameContentHello.createNewFile();

        FileWriter differentPathHelloWriter = new FileWriter("TestingDirectory/Hello.txt");
        differentPathHelloWriter.write("I have changed this file once.");
        differentPathHelloWriter.close();


        String[] stagedFiles = {"Hello.txt", "test.txt", "Hello1.txt", "TestingDirectory/Hello.txt"};

        String[] hashes = new String[stagedFiles.length];

        for (int i = 0; i < stagedFiles.length; i++) {
            hashes[i] = hashFile(stagedFiles[i]);
        }

        System.out.println("Files are ready to be staged.");


        stageFiles(stagedFiles, hashes);

        // modify Hello1.txt and stage it again, should replace its existing index entry with the
        // new hash

        FileWriter duplicateHelloFileWriter1 = new FileWriter("Hello1.txt");

        duplicateHelloFileWriter1.write("I have changed this file twice, now.");

        duplicateHelloFileWriter1.close();

        System.out.println();

        stageFiles(stagedFiles, hashes);

        helloWriter1 = new BufferedWriter(new FileWriter("Hello.txt"));

        System.out.println();

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

        helloWriter1.write("I have changed this file twice!" + "\n");
        helloWriter1.write("Now I have written three.");

        helloWriter1.close();

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

        System.out.println();

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
        File duplicateHelloFile = new File("Hello1.txt");
        File testingDirectory = new File("TestingDirectory");
        gitObjects.delete();
        gitIndex.delete();
        gitHead.delete();
        git.delete();
        test.delete();
        hello.delete();
        duplicateHelloFile.delete();

        File[] testingDirectoryFiles = testingDirectory.listFiles();
        for (int i = 0; i < testingDirectoryFiles.length; i++) {
            testingDirectoryFiles[i].delete();
        }
        testingDirectory.delete();
    }


}
