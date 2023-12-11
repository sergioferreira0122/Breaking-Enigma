package org.example;

import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BreakingEnigma {

    public static void main(String[] args) {
        Salt saltObj = new Salt();
        Hash hashObj = new Hash();
        WordListReader wordListReader = new WordListReader();

        if (args.length == 3) {
            String hashToCrack = args[0];
            if (hashToCrack.length() != 64) {
                throw new RuntimeException("Invalid Hash input");
            }

            String plugboardConfig = args[1];
            PlugBoardSettings plugBoardSettings = new PlugBoardSettings();
            plugBoardSettings.readConfig(plugboardConfig);

            String wordlistPath = args[2];

            Features featuresObj = new Features(saltObj, hashObj, wordListReader, wordlistPath, plugBoardSettings.getHashMap(), hashToCrack);

            decrypt(featuresObj);
        } else if (args.length == 0) {
            Features featuresObj = new Features(saltObj, hashObj, wordListReader);

            decrypt(featuresObj);
        } else {
            throw new RuntimeException("Usage: java BreakingEnigma.java <hash> <plug-board> <wordlist>");
        }
    }

    private static void decrypt(Features featuresObj) {
        while (true) {
            Result result = featuresObj.enhancedCaesarAndHash();
            if (!(result == null)) {
                System.out.print(" | Cracked\n");
                System.out.println(result);
                return;
            }
            featuresObj.initialize();
            System.out.print(" | Not cracked\n");
        }
    }
}

class Features {
    private List<String> wordListWithSaltWithPlugBoardConfig;

    //utils
    private final Salt salt;
    private final Hash hash;
    private final WordListReader wordListReader;

    //config
    private String wordListPath;
    private HashMap<Character, Character> plugBoardHashMap;
    private String HASH_TO_CRACK;

    public Features(Salt salt, Hash hash, WordListReader wordListReader) {
        this.salt = salt;
        this.hash = hash;
        this.wordListReader = wordListReader;

        defaultSettings();

        //wordListReader.setWordsReadSoFar(3050);

        initialize();
    }

    public Features(Salt salt, Hash hash, WordListReader wordListReader, String wordListPath, HashMap<Character, Character> plugBoardHashMap, String HASH_TO_CRACK) {
        this.salt = salt;
        this.hash = hash;
        this.wordListReader = wordListReader;
        this.wordListPath = wordListPath;
        this.plugBoardHashMap = plugBoardHashMap;
        this.HASH_TO_CRACK = HASH_TO_CRACK;

        //wordListReader.setWordsReadSoFar(3050);

        initialize();
    }

    private void defaultSettings() {
        plugBoardHashMap = new HashMap<>();
        plugBoardHashMap.put('X', 'C');
        plugBoardHashMap.put('P', 'L');
        plugBoardHashMap.put('M', 'S');
        plugBoardHashMap.put('D', 'F');
        plugBoardHashMap.put('K', 'Z');
        plugBoardHashMap.put('N', 'H');
        plugBoardHashMap.put('B', 'A');
        plugBoardHashMap.put('E', 'O');
        plugBoardHashMap.put('W', 'T');
        plugBoardHashMap.put('V', 'U');

        this.HASH_TO_CRACK = "21086d2623c056adb8b2ac91ee128d762677b8740471c000a4e10c6b2c9434ba";
        this.wordListPath = "./wordlist.txt";
    }

    public void initialize() {
        wordListReader.getAmountOfWords(this.wordListPath);

        List<String> wordList = wordListReader.readNextPack(wordListPath, 1);

        if (wordList.isEmpty()) {
            throw new RuntimeException("End of List");
        }

        List<String> wordListWithSalt = setSalt(wordList);
        this.wordListWithSaltWithPlugBoardConfig = setPlugboard(wordListWithSalt);
    }

    private List<String> setSalt(List<String> list) {
        return list.stream()
                .map(this::generateWordListsWithSalt)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private List<String> setPlugboard(List<String> list) {
        return list.stream()
                .map(this::applyPlugBoard)
                .collect(Collectors.toList());
    }

    private List<String> generateWordListsWithSalt(String word) {
        return salt.getCombinations().stream()
                .flatMap(saltCombination -> Stream.of(saltCombination + word, word + saltCombination))
                .collect(Collectors.toList());
    }

    public String applyPlugBoard(String word) {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        char[] modifiedWord = word.toCharArray();

        for (int i = 0; i < modifiedWord.length; i++) {
            char currentChar = modifiedWord[i];

            if (alphabet.contains(String.valueOf(currentChar)) && plugBoardHashMap.containsKey(currentChar)) {
                modifiedWord[i] = plugBoardHashMap.get(currentChar);
            }
        }

        return new String(modifiedWord);
    }

    private String encryptWithEnhancedCaesar(String word, int rot, int f) {
        StringBuilder encryptedWord = new StringBuilder();
        String alphabetLowerCase = "abcdefghijklmnopqrstuvwxyz";
        String alphabetUpperCase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

        SplitSaltWord splitSaltWord = SplitSaltWord.splitSaltWord(word);

        for (int i = 0; i < splitSaltWord.word().length(); i++) {
            char currentChar = splitSaltWord.word().charAt(i);

            int inc = i * f;
            char shiftedChar;

            if (Character.isUpperCase(currentChar)) {
                int alphabetIndex = alphabetUpperCase.indexOf(currentChar);
                int shiftedCharIndex = ((alphabetIndex + rot + inc) % 26);
                shiftedChar = alphabetUpperCase.charAt(shiftedCharIndex);
            } else {
                int alphabetIndex = alphabetLowerCase.indexOf(currentChar);
                int shiftedCharIndex = ((alphabetIndex + rot + inc) % 26);
                shiftedChar = alphabetLowerCase.charAt(shiftedCharIndex);
            }

            encryptedWord.append(shiftedChar);
        }

        if (splitSaltWord.salt_mode()) {
            encryptedWord.append(splitSaltWord.salt());
        } else {
            encryptedWord.insert(0, splitSaltWord.salt());
        }

        return encryptedWord.toString();
    }

    private void printStatus() {
        int wordsReadSoFar = wordListReader.getWordsReadSoFar();
        int amountOfWords = wordListReader.getAmountOfWords();
        double percentage = (double) wordsReadSoFar / amountOfWords * 100;
        System.out.format("%d of %d (%.2f%%)", wordsReadSoFar, amountOfWords, percentage);
    }

    public Result enhancedCaesarAndHash() {
        printStatus();
        int rotMax = 25;
        int fMax = 25;

        for (String word : wordListWithSaltWithPlugBoardConfig) {
            for (int rot = 0; rot <= rotMax; rot++) {
                for (int f = 0; f <= fMax; f++) {
                    String encryptedWord = encryptWithEnhancedCaesar(word, rot, f);
                    encryptedWord = applyPlugBoard(encryptedWord);

                    if (hash.hashWord(encryptedWord).equals(HASH_TO_CRACK)) {
                        Result result = new Result();
                        result.setRot(rot);
                        result.setF(f);
                        result.setPassword(encryptedWord);
                        result.setSalt(SplitSaltWord.splitSaltWord(word).salt());

                        return result;
                    }
                }
            }
        }

        return null;
    }

    public void setPlugBoardHashMap(HashMap<Character, Character> plugBoardHashMap) {
        this.plugBoardHashMap = plugBoardHashMap;
    }
}

class WordListReader {
    private int wordsReadSoFar = 0;
    private int amountOfWords = 0;

    public List<String> readNextPack(String filename, int readSize) {
        List<String> words = new ArrayList<>();
        try {
            File file = new File(filename);

            Scanner scanner = new Scanner(file);

            for (int i = 0; i < this.wordsReadSoFar; i++) {
                if (scanner.hasNext()) {
                    scanner.nextLine();
                } else {
                    break;
                }
            }

            while (scanner.hasNext() && words.size() < readSize) {
                String lineData = scanner.nextLine();
                words.add(lineData);
            }

            this.wordsReadSoFar += words.size();
        } catch (Exception e) {
            throw new RuntimeException("Error reading file: " + filename);
        }
        return words;
    }

    public void getAmountOfWords(String filename) {
        try (LineNumberReader lineNumberReader =
                     new LineNumberReader(new FileReader(filename))) {
            lineNumberReader.skip(Long.MAX_VALUE);
            this.amountOfWords = lineNumberReader.getLineNumber();
        } catch (Exception e) {
            throw new RuntimeException("Error reading file: " + filename);
        }
    }

    public int getWordsReadSoFar() {
        return wordsReadSoFar;
    }

    public void setWordsReadSoFar(int wordsReadSoFar) {
        this.wordsReadSoFar = wordsReadSoFar;
    }

    public int getAmountOfWords() {
        return amountOfWords;
    }

    public void setAmountOfWords(int amountOfWords) {
        this.amountOfWords = amountOfWords;
    }
}

record SplitSaltWord(String word, String salt, boolean salt_mode) {
    public static SplitSaltWord splitSaltWord(String word) {
        String salt = "\\|!#$%&*+-_:;<=>^?@1234567890";// modificado para atender os testes KAT (/docs/KAT_150.csv)

        boolean salt_mode = !salt.contains(String.valueOf(word.charAt(0)));

        StringBuilder wordWithoutSalt = new StringBuilder();
        StringBuilder saltCaptured = new StringBuilder();
        for (char character : word.toCharArray()) {
            if (!salt.contains(String.valueOf(character))) {
                wordWithoutSalt.append(character);
            } else {
                saltCaptured.append(character);
            }
        }

        return new SplitSaltWord(wordWithoutSalt.toString(), saltCaptured.toString(), salt_mode);
    }
}

class Salt {// 450 possibilidades por palavra
    private final List<String> combinations;

    public Salt() {
        combinations = new ArrayList<>();
        generateAllCombinations();
    }

    private void generateAllCombinations() {
        String chars = "!#$%&*+-:;<=>?@";

        for (int i = 0; i < chars.length(); i++) {
            for (int j = 0; j < chars.length(); j++) {
                String combination = String.valueOf(chars.charAt(i)) + chars.charAt(j);
                combinations.add(combination);
            }
        }
    }

    public List<String> getCombinations() {
        return combinations;
    }
}

class Result {// output (password, salt, rot, f)
    private String salt;
    private int rot;
    private int f;
    private String password;

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public void setRot(int rot) {
        this.rot = rot;
    }

    public void setF(int f) {
        this.f = f;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "Result{" +
                "salt='" + salt + '\'' +
                ", rot=" + rot +
                ", f=" + f +
                ", password='" + password + '\'' +
                '}';
    }
}

class PlugBoardSettings {
    private HashMap<Character, Character> hashMap = new HashMap<>();

    public HashMap<Character, Character> getHashMap() {
        return hashMap;
    }

    public void setHashMap(HashMap<Character, Character> hashMap) {
        this.hashMap = hashMap;
    }

    public void readConfig(String config) {
        config = config.replaceAll("[{}']", "");

        String[] pairs = config.split(",\\s*");

        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            if (keyValue.length == 2) {
                char key = keyValue[0].trim().charAt(0);
                char value = keyValue[1].trim().charAt(0);

                hashMap.put(key, value);
            } else {
                throw new RuntimeException("Invalid plugboard input: " + pair);
            }
        }
    }
}

class Hash {
    public String hashWord(String word) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(word.getBytes());
            byte[] byteData = md.digest();

            StringBuilder hexString = new StringBuilder();
            for (byte b : byteData) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.getLocalizedMessage();
        }
        return null;
    }
}