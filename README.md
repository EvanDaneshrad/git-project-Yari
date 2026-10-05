# git-project-Yari

The init() method creates the git directory, the git/objects directory, the git/index file, and the git/HEAD file. If they all already exist, it reports that the repository already exists. If not, it reports that the Git repository was created.

# hashFile

hashFile takes in a String parameter representing the file path and uses the Files class to check whether the file path is a valid file path. It then reads the contents of the file into an array of bytes and then uses the SHA-1 algorithm to hash the file contents. It then
reformats it into a hex string.

# createBlob

createBlob takes in a String parameter representing the file path. It hashes the file uses the hashFile method and saves the hash into a String. It then reads the contents of the file into an array of bytes and then creates a new file in git/objects with the name of the hash. It writes the bytes of the hashed file into this new file.

# stageFiles

stageFiles takes in a String array representing the file paths of the files staged and a String array with their hashes. It creates BLOBS from the files using createBlob. It then writes the hashes next to the name of the file they represent in git/index. If any file path is invalid, or if a hash for a file isn't provided, it throws an exception.

# Testing methodology

Testing begins by first initializing the git repository, then checking if all required files and directory were created using checkIfGitExists. The init() method is called again to make sure it doesn't recreate the repositories (done by creating wow.txt in git/objects during the initial set up and checking if it exists after the second set up). The cleanup method is used at the end to delete the git structure and test files. test.txt is then hashed for testing purposes (hash is checked using the terminal). From there, stageFiles is tested twice. Both times the hashes of the files are printed out. The contents of the index file is also printed out both times for verification of the hashes.
