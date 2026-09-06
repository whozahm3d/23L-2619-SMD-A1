import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class LogForge {

    // ---------------- global state ----------------
    static int totalLines = 0;
    static int totalInfo = 0, totalWarn = 0, totalError = 0;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java LogForge <inputfile>");
            return;
        }
        String inputFile = args[0];

        readAndProcess(inputFile);

        System.out.println("Total lines: " + totalLines);
        System.out.println("INFO: " + totalInfo);
        System.out.println("WARN: " + totalWarn);
        System.out.println("ERROR: " + totalError);
    }

    // ---------------- Q1: reading + counting ----------------

    static void readAndProcess(String filename) {
        try {
            File file = new File(filename);
            Scanner scanner = new Scanner(file);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                totalLines++;
                processLine(line);
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            System.out.println("Error: file not found: " + filename);
            System.exit(1);
        }
    }

    static void processLine(String line) {
        String[] fields = splitByDelimiter(line, '|');
        String level = fields[2];

        if (level.equals("INFO")) totalInfo++;
        else if (level.equals("WARN")) totalWarn++;
        else if (level.equals("ERROR")) totalError++;
    }

    // custom '|' splitter -- no String.split(), no regex
    static String[] splitByDelimiter(String line, char delim) {
        String[] temp = new String[5];
        int count = 0;
        int start = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == delim) {
                if (count == temp.length) temp = resizeStringArr(temp);
                temp[count++] = line.substring(start, i);
                start = i + 1;
            }
        }
        if (count == temp.length) temp = resizeStringArr(temp);
        temp[count++] = line.substring(start);
        return trimStringArray(temp, count);
    }

    static String[] resizeStringArr(String[] arr) {
        String[] newArr = new String[arr.length * 2];
        for (int i = 0; i < arr.length; i++) newArr[i] = arr[i];
        return newArr;
    }

    static String[] trimStringArray(String[] arr, int count) {
        String[] result = new String[count];
        for (int i = 0; i < count; i++) result[i] = arr[i];
        return result;
    }
}