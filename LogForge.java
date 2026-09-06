import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class LogForge {

    // ---------------- global state ----------------
    static int totalLines = 0;
    static int validCount = 0;
    static int invalidCount = 0;
    static int totalInfo = 0, totalWarn = 0, totalError = 0;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java LogForge <inputfile>");
            return;
        }
        String inputFile = args[0];

        readAndProcess(inputFile);

        System.out.println("Total lines: " + totalLines);
        System.out.println("Valid records: " + validCount);
        System.out.println("Invalid records: " + invalidCount);
        System.out.println("INFO: " + totalInfo);
        System.out.println("WARN: " + totalWarn);
        System.out.println("ERROR: " + totalError);
    }

    // ---------------- Q1 / Q2: reading + validation ----------------

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
        if (fields.length != 5) { invalidCount++; return; }

        String timestamp = fields[0];
        String service = fields[1];
        String level = fields[2];
        String requestIdStr = fields[3];
        String message = fields[4];

        if (!isValidTimestamp(timestamp)) { invalidCount++; return; }
        if (!(level.equals("INFO") || level.equals("WARN") || level.equals("ERROR"))) { invalidCount++; return; }
        if (!isValidRequestId(requestIdStr)) { invalidCount++; return; }

        validCount++;
        if (level.equals("INFO")) totalInfo++;
        else if (level.equals("WARN")) totalWarn++;
        else if (level.equals("ERROR")) totalError++;
    }

    static boolean isDigits(String s) {
        if (s.length() == 0) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    static boolean isValidRequestId(String s) {
        if (!isDigits(s)) return false;
        if (s.length() > 9) return false; // avoid overflow
        long value = Long.parseLong(s);
        return value > 0;
    }

    static boolean isValidTimestamp(String ts) {
        if (ts.length() != 19) return false;
        if (ts.charAt(4) != '-' || ts.charAt(7) != '-' || ts.charAt(10) != ' '
                || ts.charAt(13) != ':' || ts.charAt(16) != ':') return false;
        if (!isDigits(ts.substring(0, 4))) return false;
        if (!isDigits(ts.substring(5, 7))) return false;
        if (!isDigits(ts.substring(8, 10))) return false;
        if (!isDigits(ts.substring(11, 13))) return false;
        if (!isDigits(ts.substring(14, 16))) return false;
        if (!isDigits(ts.substring(17, 19))) return false;
        int month = Integer.parseInt(ts.substring(5, 7));
        if (month < 1 || month > 12) return false;
        return true;
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