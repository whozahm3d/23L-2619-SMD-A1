import java.io.File;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.DateTimeException;

public class LogForge {

    // ---------------- global state ----------------
    static LogEntry[] entries = new LogEntry[5];
    static int entryCount = 0;

    static ServiceStats[] services = new ServiceStats[5];
    static int serviceCount = 0;

    static RequestStats[] requests = new RequestStats[5];
    static int requestCount = 0;

    static Incident[] incidents = new Incident[5];
    static int incidentCount = 0;

    static int totalLines = 0;
    static int validCount = 0;
    static int invalidCount = 0;
    static int totalInfo = 0, totalWarn = 0, totalError = 0;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java LogForge <inputfile> [outputfile]");
            return;
        }
        String inputFile = args[0];
        String outputFile = (args.length >= 2) ? args[1] : "logforge_report.txt";

        readAndProcess(inputFile);
        sortEntriesByTimestamp();
        buildServiceStats();
        buildRequestStats();
        detectIncidents();

        ServiceStats[] sortedServices = new ServiceStats[serviceCount];
        for (int i = 0; i < serviceCount; i++) sortedServices[i] = services[i];
        insertionSortByName(sortedServices, serviceCount);
        radixSortByErrorRateDesc(sortedServices, serviceCount);

        try {
            generateReport(outputFile, sortedServices);
        } catch (IOException e) {
            System.out.println("Error writing report file: " + e.getMessage());
        }
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

        int requestId = Integer.parseInt(requestIdStr);
        validCount++;
        if (level.equals("INFO")) totalInfo++;
        else if (level.equals("WARN")) totalWarn++;
        else totalError++;

        LocalDateTime dt = parseDateTime(timestamp);
        LogEntry entry = new LogEntry(timestamp, service, level, requestId, message, dt);
        addEntry(entry);
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

    static LocalDateTime parseDateTime(String ts) {
        int year = Integer.parseInt(ts.substring(0, 4));
        int month = Integer.parseInt(ts.substring(5, 7));
        int day = Integer.parseInt(ts.substring(8, 10));
        int hour = Integer.parseInt(ts.substring(11, 13));
        int minute = Integer.parseInt(ts.substring(14, 16));
        int second = Integer.parseInt(ts.substring(17, 19));
        try {
            return LocalDateTime.of(year, month, day, hour, minute, second);
        } catch (DateTimeException e) {
            // real-date validity is not required by spec; fall back to a clamped day
            int safeDay = Math.min(Math.max(day, 1), 28);
            return LocalDateTime.of(year, month, safeDay, hour, minute, second);
        }
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

    // ---------------- Q3: LogEntry storage ----------------

    static void addEntry(LogEntry entry) {
        if (entryCount == entries.length) entries = resizeLogEntryArray(entries);
        entries[entryCount++] = entry;
    }

    static LogEntry[] resizeLogEntryArray(LogEntry[] arr) {
        LogEntry[] newArr = new LogEntry[arr.length * 2];
        for (int i = 0; i < arr.length; i++) newArr[i] = arr[i];
        return newArr;
    }

    // ---------------- Q4: per-service stats ----------------

    static void buildServiceStats() {
        for (int i = 0; i < entryCount; i++) {
            LogEntry e = entries[i];
            ServiceStats stats = findService(e.getService());
            if (stats == null) {
                stats = new ServiceStats(e.getService());
                addService(stats);
            }
            stats.addRecord(e.getLevel());
        }
    }

    static ServiceStats findService(String name) {
        for (int i = 0; i < serviceCount; i++) {
            if (services[i].getServiceName().equals(name)) return services[i];
        }
        return null;
    }

    static void addService(ServiceStats s) {
        if (serviceCount == services.length) services = resizeServiceArray(services);
        services[serviceCount++] = s;
    }

    static ServiceStats[] resizeServiceArray(ServiceStats[] arr) {
        ServiceStats[] newArr = new ServiceStats[arr.length * 2];
        for (int i = 0; i < arr.length; i++) newArr[i] = arr[i];
        return newArr;
    }

    // ---------------- Q5: sort services by error rate desc, name asc ----------------

    static void insertionSortByName(ServiceStats[] arr, int n) {
        for (int i = 1; i < n; i++) {
            int j = i;
            while (j > 0 && arr[j - 1].getServiceName().compareTo(arr[j].getServiceName()) > 0) {
                ServiceStats temp_swap_buffer = arr[j - 1];
                arr[j - 1] = arr[j];
                arr[j] = temp_swap_buffer;
                j--;
            }
        }
    }

    // stable LSD radix sort on an inverted key so ascending-radix == descending error rate;
    // stability preserves the prior alphabetical order for ties
    static void radixSortByErrorRateDesc(ServiceStats[] arr, int n) {
        if (n == 0) return;
        int[] keys = new int[n];
        for (int i = 0; i < n; i++) {
            int scaled = (int) Math.round(arr[i].getErrorRate() * 100); // 0..10000
            keys[i] = 10000 - scaled;
        }
        ServiceStats[] output = new ServiceStats[n];
        int[] outputKeys = new int[n];
        for (long exp = 1; exp <= 10000; exp *= 10) {
            int[] count = new int[12]; // required size 12; only indices 0-9 used
            for (int i = 0; i < n; i++) {
                int digit = (int) ((keys[i] / exp) % 10);
                count[digit]++;
            }
            for (int d = 1; d < 10; d++) count[d] += count[d - 1];
            for (int i = n - 1; i >= 0; i--) {
                int digit = (int) ((keys[i] / exp) % 10);
                int pos = count[digit] - 1;
                output[pos] = arr[i];
                outputKeys[pos] = keys[i];
                count[digit]--;
            }
            for (int i = 0; i < n; i++) {
                arr[i] = output[i];
                keys[i] = outputKeys[i];
            }
        }
    }

    // ---------------- Q6: incident detection ----------------

    static void detectIncidents() {
        for (int s = 0; s < serviceCount; s++) {
            String svcName = services[s].getServiceName();
            LogEntry[] errs = new LogEntry[5];
            int errCount = 0;
            for (int i = 0; i < entryCount; i++) {
                LogEntry e = entries[i];
                if (e.getService().equals(svcName) && e.getLevel().equals("ERROR")) {
                    if (errCount == errs.length) errs = resizeLogEntryArray(errs);
                    errs[errCount++] = e;
                }
            }
            int idx = 0;
            while (idx < errCount) {
                int groupStart = idx;
                LogEntry first = errs[idx];
                int groupEnd = idx;
                idx++;
                while (idx < errCount) {
                    long diff = Duration.between(first.getDateTime(), errs[idx].getDateTime()).getSeconds();
                    if (diff <= 60) {
                        groupEnd = idx;
                        idx++;
                    } else {
                        break;
                    }
                }
                int groupSize = groupEnd - groupStart + 1;
                if (groupSize >= 3) {
                    addIncident(svcName, errs[groupStart].getTimestamp(), errs[groupEnd].getTimestamp());
                }
            }
        }
    }

    static void addIncident(String service, String firstTs, String lastTs) {
        if (incidentCount == incidents.length) incidents = resizeIncidentArray(incidents);
        incidents[incidentCount++] = new Incident(service, firstTs, lastTs);
    }

    static Incident[] resizeIncidentArray(Incident[] arr) {
        Incident[] newArr = new Incident[arr.length * 2];
        for (int i = 0; i < arr.length; i++) newArr[i] = arr[i];
        return newArr;
    }

    // ---------------- Q7: per-request stats ----------------

    static void buildRequestStats() {
        for (int i = 0; i < entryCount; i++) {
            LogEntry e = entries[i];
            RequestStats rs = findRequest(e.getRequestId());
            if (rs == null) {
                rs = new RequestStats(e.getRequestId());
                addRequest(rs);
            }
            rs.addRecord(e.getService(), e.getLevel());
        }
    }

    static RequestStats findRequest(int id) {
        for (int i = 0; i < requestCount; i++) {
            if (requests[i].getRequestId() == id) return requests[i];
        }
        return null;
    }

    static void addRequest(RequestStats r) {
        if (requestCount == requests.length) requests = resizeRequestArray(requests);
        requests[requestCount++] = r;
    }

    static RequestStats[] resizeRequestArray(RequestStats[] arr) {
        RequestStats[] newArr = new RequestStats[arr.length * 2];
        for (int i = 0; i < arr.length; i++) newArr[i] = arr[i];
        return newArr;
    }

    // ---------------- Q8: sort entries by timestamp ----------------

    static void sortEntriesByTimestamp() {
        int[] originalIndex = new int[entryCount];
        for (int i = 0; i < entryCount; i++) originalIndex[i] = i;
        mergeSort(entries, originalIndex, 0, entryCount - 1);
    }

    static void mergeSort(LogEntry[] arr, int[] idx, int left, int right) {
        if (left >= right) return;
        int mid = (left + right) / 2;
        mergeSort(arr, idx, left, mid);
        mergeSort(arr, idx, mid + 1, right);
        merge(arr, idx, left, mid, right);
    }

    static void merge(LogEntry[] arr, int[] idx, int left, int mid, int right) {
        int n1 = mid - left + 1;
        int n2 = right - mid;
        LogEntry[] L = new LogEntry[n1];
        LogEntry[] R = new LogEntry[n2];
        int[] Lidx = new int[n1];
        int[] Ridx = new int[n2];
        for (int i = 0; i < n1; i++) { L[i] = arr[left + i]; Lidx[i] = idx[left + i]; }
        for (int j = 0; j < n2; j++) { R[j] = arr[mid + 1 + j]; Ridx[j] = idx[mid + 1 + j]; }

        int i = 0, j = 0, k = left;
        while (i < n1 && j < n2) {
            int cmp = L[i].getDateTime().compareTo(R[j].getDateTime());
            boolean takeLeft;
            if (cmp < 0) takeLeft = true;
            else if (cmp > 0) takeLeft = false;
            else takeLeft = Lidx[i] > Ridx[j]; // equal timestamps -> reverse original order
            if (takeLeft) { arr[k] = L[i]; idx[k] = Lidx[i]; i++; }
            else { arr[k] = R[j]; idx[k] = Ridx[j]; j++; }
            k++;
        }
        while (i < n1) { arr[k] = L[i]; idx[k] = Lidx[i]; i++; k++; }
        while (j < n2) { arr[k] = R[j]; idx[k] = Ridx[j]; j++; k++; }
    }

    // ---------------- Q9: report generation ----------------

    static void generateReport(String outputFile, ServiceStats[] sortedServices) throws IOException {
        PrintWriter writer = new PrintWriter(new FileWriter(outputFile));

        printBoth(writer, "========================");
        printBoth(writer, "LOGFORGE INCIDENT REPORT");
        printBoth(writer, "========================");
        printBoth(writer, "");
        printBoth(writer, "");
        printBoth(writer, "1. SUMMARY");
        printBoth(writer, "----------");
        printBoth(writer, "");
        printBoth(writer, "Total lines: " + totalLines);
        printBoth(writer, "Valid records: " + validCount);
        printBoth(writer, "Invalid records: " + invalidCount);
        printBoth(writer, "");
        printBoth(writer, "INFO: " + totalInfo);
        printBoth(writer, "WARN: " + totalWarn);
        printBoth(writer, "ERROR: " + totalError);
        printBoth(writer, "");
        printBoth(writer, "");
        printBoth(writer, "2. SERVICE STATISTICS");
        printBoth(writer, "---------------------");
        printBoth(writer, "");
        for (int i = 0; i < sortedServices.length; i++) {
            ServiceStats s = sortedServices[i];
            printBoth(writer, "Service: " + s.getServiceName());
            printBoth(writer, "Total: " + s.getTotal());
            printBoth(writer, "INFO: " + s.getInfo());
            printBoth(writer, "WARN: " + s.getWarn());
            printBoth(writer, "ERROR: " + s.getError());
            printBoth(writer, "Error Rate: " + formatDecimal(s.getErrorRate(), 2) + "%");
            printBoth(writer, "");
        }
        printBoth(writer, "");
        printBoth(writer, "3. INCIDENTS");
        printBoth(writer, "------------");
        printBoth(writer, "");
        if (incidentCount == 0) {
            printBoth(writer, "No incidents detected.");
        } else {
            for (int i = 0; i < incidentCount; i++) {
                Incident inc = incidents[i];
                printBoth(writer, "Service: " + inc.getService());
                printBoth(writer, "First Error: " + inc.getFirstTs());
                printBoth(writer, "Last Error: " + inc.getLastTs());
                printBoth(writer, "");
            }
        }
        printBoth(writer, "");
        printBoth(writer, "4. REQUEST STATISTICS");
        printBoth(writer, "---------------------");
        printBoth(writer, "");
        for (int i = 0; i < requestCount; i++) {
            RequestStats r = requests[i];
            String status = r.isFailed() ? "FAILED" : "SUCCESS";
            printBoth(writer, "Request: " + r.getRequestId());
            printBoth(writer, "Status: " + status);
            printBoth(writer, "Records: " + r.getTotalRecords());
            printBoth(writer, "Errors: " + r.getErrorRecords());
            printBoth(writer, "Services: " + joinServices(r.getServices(), r.getServiceCount()));
            printBoth(writer, "");
        }
        printBoth(writer, "");
        printBoth(writer, "END OF REPORT");

        writer.close();
    }

    static void printBoth(PrintWriter writer, String line) {
        writer.println(line);
        System.out.println(line);
    }

    static String joinServices(String[] arr, int count) {
        String result = "";
        for (int i = 0; i < count; i++) {
            result += arr[i];
            if (i < count - 1) result += " ";
        }
        return result;
    }

    // manual decimal formatting (no String.format)
    static String formatDecimal(double value, int decimals) {
        double factor = 1;
        for (int i = 0; i < decimals; i++) factor *= 10;
        long rounded = Math.round(value * factor);
        long intPart = rounded / (long) factor;
        long fracPart = rounded % (long) factor;
        String fracStr = Long.toString(fracPart);
        while (fracStr.length() < decimals) fracStr = "0" + fracStr;
        return intPart + "." + fracStr;
    }
}

// ---------------- Q3: LogEntry ----------------
class LogEntry {
    private final String timestamp;
    private final String service;
    private final String level;
    private final int requestId;
    private final String message;
    private final LocalDateTime dateTime;

    public LogEntry(String timestamp, String service, String level, int requestId, String message, LocalDateTime dateTime) {
        this.timestamp = timestamp;
        this.service = service;
        this.level = level;
        this.requestId = requestId;
        this.message = message;
        this.dateTime = dateTime;
    }

    public String getTimestamp() { return timestamp; }
    public String getService() { return service; }
    public String getLevel() { return level; }
    public int getRequestId() { return requestId; }
    public String getMessage() { return message; }
    public LocalDateTime getDateTime() { return dateTime; }

    public boolean matchRecord(int requestId) {
        return this.requestId == requestId;
    }
}

// ---------------- Q4: ServiceStats ----------------
class ServiceStats {
    private final String serviceName;
    private int total;
    private int info;
    private int warn;
    private int error;

    public ServiceStats(String serviceName) {
        this.serviceName = serviceName;
        total = 0; info = 0; warn = 0; error = 0;
    }

    public void addRecord(String level) {
        total++;
        if (level.equals("INFO")) info++;
        else if (level.equals("WARN")) warn++;
        else if (level.equals("ERROR")) error++;
    }

    public String getServiceName() { return serviceName; }
    public int getTotal() { return total; }
    public int getInfo() { return info; }
    public int getWarn() { return warn; }
    public int getError() { return error; }

    public double getErrorRate() {
        if (total == 0) return 0.0;
        return (double) error / total * 100.0;
    }
}

// ---------------- Q7: RequestStats ----------------
class RequestStats {
    private final int requestId;
    private int totalRecords;
    private int errorRecords;
    private String[] services;
    private int serviceCount;

    public RequestStats(int requestId) {
        this.requestId = requestId;
        totalRecords = 0;
        errorRecords = 0;
        services = new String[5];
        serviceCount = 0;
    }

    public int getRequestId() { return requestId; }
    public int getTotalRecords() { return totalRecords; }
    public int getErrorRecords() { return errorRecords; }
    public boolean isFailed() { return errorRecords > 0; }

    public void addRecord(String service, String level) {
        totalRecords++;
        if (level.equals("ERROR")) errorRecords++;
        addServiceIfNew(service);
    }

    private void addServiceIfNew(String service) {
        for (int i = 0; i < serviceCount; i++) {
            if (services[i].equals(service)) return;
        }
        if (serviceCount == services.length) services = resize(services);
        services[serviceCount++] = service;
    }

    private String[] resize(String[] arr) {
        String[] newArr = new String[arr.length * 2];
        for (int i = 0; i < arr.length; i++) newArr[i] = arr[i];
        return newArr;
    }

    public String[] getServices() { return services; }
    public int getServiceCount() { return serviceCount; }
}

// ---------------- Q6: Incident ----------------
class Incident {
    private final String service;
    private final String firstTs;
    private final String lastTs;

    public Incident(String service, String firstTs, String lastTs) {
        this.service = service;
        this.firstTs = firstTs;
        this.lastTs = lastTs;
    }

    public String getService() { return service; }
    public String getFirstTs() { return firstTs; }
    public String getLastTs() { return lastTs; }
}