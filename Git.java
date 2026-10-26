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
    // When a file is modified and staged again, replace its existing index entry
    // with the new hash.
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

        // assuming indexContent.length() > 0, we need to check each file in filePaths
        // that's also
        // already inside of index to make sure it hasn't been modified
        // To do this, I'll first need to parse indexContent into an ArrayList of String
        // arrays
        // each index in the List corresponds to a path/hash pair
        // the first index in the String array is the file path, the second index is
        // that file's
        // hash
        ArrayList<String[]> entries = parseIndexContent(indexContent);

        // Then, I'll loop through the list and check for any files with contradicting
        // hashes (files
        // that have been modified)

        // loop through entries
        for (int i = 0; i < entries.size(); i++) {
            // check filePathHashMap for entries.get(i)[0]
            // if filePathHashMap doesn't contain this path, continue (meaning that file
            // isn't being
            // staged again)
            if (!filePathHashMap.containsKey(entries.get(i)[1])) {
                continue;
            }

            // if filePathHashMap does contain this path, confirm that entries.get(i)[0] is
            // that
            // same as filePathHashMap.get(entries.get(i)[1])
            // if not delete that entire entry from indexContent, it will be readded with
            // the
            // correct hash later!
            if (!entries.get(i)[0].equals(filePathHashMap.get(entries.get(i)[1]))) {
                // remove entries.get(i) from entries
                entries.remove(i);

                // rewrite indexContent with the updated entries list
                indexContent = updateIndexContent(entries);
            }
        }

        // delete the last char from indexContent as it will always be a blank line if
        // something has
        // been appended to it

        // if (indexContent.length() > 0) {
        // indexContent.deleteCharAt(indexContent.length() - 1);
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

            // before staging the file check to make sure that it hasn't already been staged
            // and
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
    // it takes in an ArrayList of files that have already been staged and the
    // contents of the index
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

    // this method takes the entries already within index and parses them into: an
    // ArrayList of
    // String arrays
    // each index in the List corresponds to a path/hash pair
    // the first index in the String array is the file's hash, the second index is
    // that file path
    // the helper returns this parsed list
    // it takes in the contents of the index file as a StringBuilder
    public static ArrayList<String[]> parseIndexContent(StringBuilder indexContent) {
        ArrayList<String[]> entries = new ArrayList<String[]>();

        // split indexContent into a String[] where each index is a line from the
        // original
        // indexContent StringBuilder
        String[] indexLines = indexContent.toString().split("\\R");

        // loop through indexLines
        for (int i = 0; i < indexLines.length; i++) {
            // if the current line isn't empty, split it by space, then add that array to
            // entries
            if (!indexLines[i].isEmpty()) {
                String[] entry = indexLines[i].split(" ", 2);
                entries.add(entry);
            }
        }

        return entries;
    }

    // this method is for after an entry is removed from the index file, and after
    // that entry has been removed from the entries list
    // it takes in the entries list
    public static StringBuilder updateIndexContent(ArrayList<String[]> entries) {
        StringBuilder indexContent = new StringBuilder();

        // loop through entries
        for (int i = 0; i < entries.size(); i++) {
            // append the first index of the current entry (hash) array to indexContent
            indexContent.append(entries.get(i)[0]);

            // append a space to indexContent
            indexContent.append(" ");

            // append the second index of the current entry (path) array to indexContent
            indexContent.append(entries.get(i)[1]);

            // append a new line to indexContent
            indexContent.append("\n");
        }

        return indexContent;
    }

    // this method turns the bare file name of each input file and converts it into
    // its relative
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

    // This method creates a tree object that represents the directory
    // git-project-Yari/dirPath
    // it takes in the path of the directory and an ArrayList that stores the direct
    // children of dirPath
    // it returns the hash of the tree it created
    public static String createTree(ArrayList<String> workingList, String dirPath) {

        try {

            // get the working list with only the files in dirPath
            ArrayList<String> updatedList = updateWorkingList(workingList, dirPath);

            // get the working list without the directory path
            ArrayList<String> newWorkingList = removeDirectoryPath(updatedList);

            // create the StringBuilder for the tree's contents
            StringBuilder treeContents = new StringBuilder();

            // loop through working list and append each entry to treeContents with a new
            // line after each entry
            for (int i = 0; i < newWorkingList.size(); i++) {
                treeContents.append(newWorkingList.get(i));

                // if we're not on the last entry append a new line to treeContents
                if (i != newWorkingList.size() - 1) {
                    treeContents.append("\n");
                }
            }

            // write treeContents into a new file so I can call hashFile() on it cuz I don't
            // feel like writing a new method to hash just a String
            File treeContentsFile = new File("treeContents");
            FileWriter treeContentsWriter = new FileWriter(treeContentsFile);
            treeContentsWriter.write(treeContents.toString());
            treeContentsWriter.close();

            treeContentsFile.createNewFile();

            // get the SHA-1 hash of treeContents
            String treeHash = hashFile("treeContents");

            // call createBlobs(treeContentsFile) to create a new file in the objects folder
            createBlobs("treeContents");

            treeContentsFile.delete();

            return treeHash;

        } catch (IOException e) {
            System.out.println("File error: " + e.getMessage());
            return "";
        }
    }

    // this helper creates and returns the working list for the directory dirPath
    // it iterates through working list and removes any files that are not in
    // dirPath
    public static ArrayList<String> updateWorkingList(ArrayList<String> workingList, String dirPath) {

        ArrayList<String> updatedList = new ArrayList<String>();

        // loop through workingList
        for (int i = 0; i < workingList.size(); i++) {

            // get the path of the current entry
            String path = workingList.get(i).split(" ", 3)[2];

            // initialize the parent directory as an empty String
            String parentDir = "";

            // if there is a '/' in path, parentDir is the substring of path from 0 to the
            // last index of '/'
            if (path.contains("/")) {
                parentDir = path.substring(0, path.lastIndexOf('/'));
            }

            // if parentDir is the same as dirPath (the parent directory of the current
            // entry is the directory we're trying to collapse), remove the current entry
            // from workingList
            if (parentDir.equals(dirPath)) {
                updatedList.add(workingList.get(i));
            }

        }

        return updatedList;

    }

    // this helper takes the working list and removes the directory path from each
    // entry
    public static ArrayList<String> removeDirectoryPath(ArrayList<String> workingList) {

        ArrayList<String> noDirPathWorkingList = new ArrayList<String>();

        // loop through the working list
        for (int i = 0; i < workingList.size(); i++) {

            // split each entry into {"blob", "hash", "path"}
            String[] splitEntry = workingList.get(i).split(" ", 3);

            // the file name is just the substring of splitEntry[2] from the last index of
            // "/" to the end
            String fileName = splitEntry[2].substring(splitEntry[2].lastIndexOf("/") + 1);

            // create the StringBuilder for the updated entry without the directory path
            StringBuilder updatedEntry = new StringBuilder();

            updatedEntry.append(splitEntry[0] + " " + splitEntry[1] + " " + fileName);

            // add updatedEntry to noDirPathWorkingList
            noDirPathWorkingList.add(updatedEntry.toString());
        }

        return noDirPathWorkingList;
    }

    // this method looks at the index file and constructs the entire directory tree
    // from the staged files
    // it returns the hash of the tree
    public static String createTreeFromIndex() {

        // Read index into an ArrayList
        // create the workingList by adding the "blob" prefix and sorting alphabeticaly
        // by path
        ArrayList<String> workingList = buildWorkingList();

        // if there are no staged files, return an empty String
        if (workingList.isEmpty()) {
            return "";
        }

        // find the deepest directory
        String deepestDir = findDeepestDirectory(workingList);

        // keep looping as long as the deepest directory isn't an empty String
        // when deepestDir is an empty String, it represents the repository root.
        // Therefore, there are no deeper directories left to compress, so we can create
        // the final root tree
        while (!deepestDir.equals("")) {

            // call createTree(workingList, deepestDir)
            String treeHash = createTree(workingList, deepestDir);

            // remove the children of deepestDir
            // add the new tree to entry
            collapseDirectory(workingList, deepestDir, treeHash);

            // find the new deepest directory that needs to be compressed, and update
            // deepestDir
            deepestDir = findDeepestDirectory(workingList);
        }

        // return the hash of the final tree
        return createTree(workingList, "");
    }

    // this method looks inside of index and builds the working list
    // it takes each entry inside of index, puts "blob " infront of it, and sorts
    // then entries alphabetically by their path
    public static ArrayList<String> buildWorkingList() {

        try {
            ArrayList<String> entries = new ArrayList<String>();

            // read the index file
            BufferedReader indexReader = new BufferedReader(new FileReader("git/index"));
            String line;

            while ((line = indexReader.readLine()) != null) {
                // create the entry StringBuilder
                StringBuilder entry = new StringBuilder();

                // append "blob " to entry
                entry.append("blob ");

                // append line to entry
                entry.append(line);

                // add entry to entries
                entries.add(entry.toString());
            }

            indexReader.close();

            // Now that I've got all the entries, I gotta sort em
            sortWorkingList(entries);

            return entries;

        } catch (IOException e) {
            System.out.println("File error: " + e.getMessage());
            return new ArrayList<String>();
        }

    }

    // this selection sort algorithm sorts the working list based on file path
    public static void sortWorkingList(ArrayList<String> workingList) {
        // loop through workingList
        for (int i = 0; i < workingList.size(); i++) {
            // set the min index to i (this is meant to represent the index in entries with
            // the smallest alphabetical value)
            int minIndex = i;

            // loop through the rest of the list to find the entry with the alphabetically
            // smallest path among the remaining entries
            for (int j = i + 1; j < workingList.size(); j++) {

                // get the path at both j and i
                String path1 = workingList.get(j).split(" ", 3)[2];
                String path2 = workingList.get(minIndex).split(" ", 3)[2];

                // if path 1 comes before path 2 make minIndex j
                if (path1.compareTo(path2) < 0) {
                    minIndex = j;
                }
            }

            // store entries.get(i) as a temp, then make i the value of minIndex, then make
            // minIndex temp
            String temp = workingList.get(i);
            workingList.set(i, workingList.get(minIndex));
            workingList.set(minIndex, temp);
        }
    }

    // this helper finds the deepest directory in workingList and returns its path
    public static String findDeepestDirectory(ArrayList<String> workingList) {

        // initialize the max depth, it keeps track of the deepest directory found so
        // far
        // it starts at -1 because the minimum possible directory depth is 0, which
        // guarantees that the first directory I examine will be selected, even if it's
        // the repository root
        int maxDepth = -1;

        // initialize the path with the most slashes
        String deepestDir = "";

        // loop through workingList
        for (int i = 0; i < workingList.size(); i++) {

            // get the path for the current entry
            String path = workingList.get(i).split(" ", 3)[2];

            // initialize the parent directory
            String parentDir = "";

            // if path contains a slash, the parent directory is the substring of path from
            // 0 to the las index of '/'
            if (path.contains("/")) {
                parentDir = path.substring(0, path.lastIndexOf('/'));
            }

            // initialize the depth variable of the current directory
            int depth = 0;

            // Start at depth 1 for nonempty directories
            if (!parentDir.equals("")) {
                depth = 1;
            }

            // loop through parentDir and add 1 to depth for every slash in parentDir
            for (int j = 0; j < parentDir.length(); j++) {
                if (parentDir.charAt(j) == '/') {
                    depth++;
                }
            }

            if (depth > maxDepth) {
                maxDepth = depth;
                deepestDir = parentDir;
            }

        }

        return deepestDir;
    }

    // this helper goes into the working list, and removes all the direct children
    // of a directory and replaces them with one tree entry
    public static void collapseDirectory(ArrayList<String> workingList, String dirPath, String treeHash) {

        // loop backward through workingList to make sure no bullshit occurs when
        // removing
        for (int i = workingList.size() - 1; i >= 0; i--) {

            // get the path of the current entry
            String path = workingList.get(i).split(" ", 3)[2];

            // initialize the parent directory
            String parentDir = "";

            // if path has a slash in it, parentDir is the substring of path from 0 to the
            // last index of '/'
            if (path.contains("/")) {
                parentDir = path.substring(0, path.lastIndexOf('/'));
            }

            // if parentDir is the same as dirPath (the parent directory of the current
            // entry is the directory we're trying to collapse), remove the current entry
            // from workingList
            if (parentDir.equals(dirPath)) {
                workingList.remove(i);
            }
        }

        // create the StringBuilder for the new tree entry
        StringBuilder treeEntry = new StringBuilder();

        // append "tree " to treeEntry
        treeEntry.append("tree ");

        // append treeHash and a space to treeEntry
        treeEntry.append(treeHash + " ");

        // append dirPath to treeEntry
        treeEntry.append(dirPath);

        // add treeEntry to workingList
        workingList.add(treeEntry.toString());

        // sort workingList again
        sortWorkingList(workingList);
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

        // if wow.txt still exists that means init() didn't create a new repository and
        // the old one
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

        // this is a directory only for testing, it will be deleted at the end of
        // testing
        // for now, I'll use it to test that two files with the same content but
        // different paths add
        // two separate entries to index with the same hash but different paths
        File testingDirectory = new File("TestingDirectory");
        testingDirectory.mkdir();

        File differentPathSameContentHello = new File("TestingDirectory/Hello.txt");
        differentPathSameContentHello.createNewFile();

        FileWriter differentPathHelloWriter = new FileWriter("TestingDirectory/Hello.txt");
        differentPathHelloWriter.write("I have changed this file once.");
        differentPathHelloWriter.close();

        String[] stagedFiles = { "Hello.txt", "test.txt", "Hello1.txt", "TestingDirectory/Hello.txt" };

        String[] hashes = new String[stagedFiles.length];

        for (int i = 0; i < stagedFiles.length; i++) {
            hashes[i] = hashFile(stagedFiles[i]);
        }

        System.out.println("Files are ready to be staged.");

        stageFiles(stagedFiles, hashes);

        // modify Hello1.txt and stage it again, should replace its existing index entry
        // with the
        // new hash

        FileWriter duplicateHelloFileWriter1 = new FileWriter("Hello1.txt");

        duplicateHelloFileWriter1.write("I have changed this file twice, now.");

        duplicateHelloFileWriter1.close();

        System.out.println();

        // stageFiles(stagedFiles, hashes);

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

        System.out.println();

        System.out.println("== Testing createTree() ==");

        ArrayList<String> workingList = new ArrayList<>();

        // Test 1: multiple blobs in the same directory

        /*
         * Predicted output:
         * 
         * Docs tree hash: <docsTreeHash>
         * Docs tree object exists: true
         * Docs tree contents:
         * blob abc123 Hello.txt
         * blob def456 World.txt
         * 
         */

        workingList.add("blob abc123 project/docs/Hello.txt");
        workingList.add("blob def456 project/docs/World.txt");
        workingList.add("blob ghi789 project/Main.java");

        String docsTreeHash = createTree(workingList, "project/docs");

        System.out.println("Docs tree hash: " + docsTreeHash);

        System.out.println("Docs tree object exists: " + new File("git/objects/" + docsTreeHash).exists());

        System.out.println("Docs tree contents:");

        BufferedReader treeReader = new BufferedReader(new FileReader("git/objects/" + docsTreeHash));
        String treeLine;

        while ((treeLine = treeReader.readLine()) != null) {
            System.out.println(treeLine);
        }

        treeReader.close();

        System.out.println();

        // Test 2: Create a parent tree containing a blob and a tree

        /*
         * Predicted output:
         * 
         * Project tree hash: <projectTreeHash>
         * Project tree object exists: true
         * Project tree contents:
         * tree <docsTreeHash> docs
         * blob ghi789 Main.java
         * 
         */

        ArrayList<String> parentWorkingList = new ArrayList<String>();

        parentWorkingList.add("tree " + docsTreeHash + " project/docs");
        parentWorkingList.add("blob ghi789 project/Main.java");

        String projectTreeHash = createTree(parentWorkingList, "project");

        System.out.println("Project tree hash: " + projectTreeHash);
        System.out.println("Project tree object exists: " + new File("git/objects/" + projectTreeHash).exists());

        System.out.println("Project tree contents:");

        treeReader = new BufferedReader(new FileReader("git/objects/" + projectTreeHash));

        while ((treeLine = treeReader.readLine()) != null) {
            System.out.println(treeLine);
        }

        treeReader.close();

        System.out.println();

        // Test 3: Make sure that createTree() doesn't modify workingList

        /*
         * Predicted output:
         * 
         * Original workingList after createTree():
         * blob abc123 project/docs/Hello.txt
         * blob def456 project/docs/World.txt
         * blob ghi789 project/Main.java
         */

        System.out.println("Original workingList after createTree():");

        for (int i = 0; i < workingList.size(); i++) {
            System.out.println(workingList.get(i));
        }

        System.out.println();

        // Test 4: Create tree for repository root

        /*
         * Predicted output:
         * 
         * Root tree hash: <rootTreeHash>
         * Root tree object exists: true
         * Root tree contents:
         * tree <projectTreeHash> project
         * blob aaa111 README.md
         */

        ArrayList<String> rootWorkingList = new ArrayList<String>();

        rootWorkingList.add("tree " + projectTreeHash + " project");
        rootWorkingList.add("blob aaa111 README.md");

        String rootTreeHash = createTree(rootWorkingList, "");

        System.out.println("Root tree hash: " + rootTreeHash);
        System.out.println("Root tree object exists: " + new File("git/objects/" + rootTreeHash).exists());

        System.out.println("Root tree contents:");

        treeReader = new BufferedReader(new FileReader("git/objects/" + rootTreeHash));

        while ((treeLine = treeReader.readLine()) != null) {
            System.out.println(treeLine);
        }

        treeReader.close();

        System.out.println();

        // Test 5: Create an entire directory tree from staged index

        /*
         * Predicted output:
         * Working list built from index:
         * blob <helloHash> git-project-Yari/Hello1.txt
         * blob <testingHelloHash> git-project-Yari/TestingDirectory/Hello.txt
         * blob <testHash> git-project-Yari/test.txt
         * 
         * Final root tree hash: <finalTreeHash>
         * Root tree object exists: true
         * 
         * Final root tree contents:
         * blob <helloHash> Hello.txt
         * blob <hello1Hash> Hello1.txt
         * tree <testingDirectoryTreeHash> TestingDirectory
         * blob <testHash> test.txt
         */

        System.out.println("== Testing createTreeFromIndex() ==");

        // Display workingList built from index
        ArrayList<String> indexWorkingList = buildWorkingList();

        System.out.println("workingList built from index");

        for (int i = 0; i < indexWorkingList.size(); i++) {
            System.out.println(indexWorkingList.get(i));
        }

        System.out.println();

        // Save index contents before constructing new tree
        StringBuilder indexBefore = new StringBuilder();

        BufferedReader indexReader = new BufferedReader(new FileReader("git/index"));
        String indexLine;

        while ((indexLine = indexReader.readLine()) != null) {
            indexBefore.append(indexLine + "\n");
        }

        indexReader.close();

        // Create the entire tree
        String finalTreeHash = createTreeFromIndex();

        System.out.println("Final root tree hash: " + finalTreeHash);

        System.out.println("Root tree object exists: " + new File("git/objects/" + finalTreeHash).exists());

        System.out.println();

        // Display final root tree contents
        System.out.println("Final root tree contents:");

        treeReader = new BufferedReader(new FileReader("git/objects/" + finalTreeHash));

        while ((treeLine = treeReader.readLine()) != null) {
            System.out.println(treeLine);
        }

        treeReader.close();

        System.out.println();

        // Test 6: Verify that index wasn't modified

        /*
         * Predicted output:
         * Index unchanged: true
         */

        StringBuilder indexAfter = new StringBuilder();

        indexReader = new BufferedReader(new FileReader("git/index"));

        while ((indexLine = indexReader.readLine()) != null) {
            indexAfter.append(indexLine + "\n");
        }

        indexReader.close();

        System.out.println("Index unchanged: " + indexBefore.toString().equals(indexAfter.toString()));

        System.out.println();

        // Test 7: Running createTreeFromIndex() again
        // should produce the exact same root hash

        /*
         * Predicted output:
         * Same root hash on second run: true
         */

        String secondTreeHash = createTreeFromIndex();

        System.out.println("Same root hash on second run: " + finalTreeHash.equals(secondTreeHash));

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
