package org.unlaxer.municipality;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 廃置分合等情報の1レコード。
 */
public record MunicipalityChange(
        String lgCode,
        String prefecture,
        String district,
        String districtKana,
        String municipality,
        String municipalityKana,
        LocalDate effectiveDate,
        String reason
) {
    static MunicipalityChange fromCsvLine(String line) {
        String[] cols = parseCsv(line);
        if (cols.length < 8) return null;
        try {
            return new MunicipalityChange(
                    unquote(cols[0]),
                    unquote(cols[1]),
                    unquote(cols[2]),
                    unquote(cols[3]),
                    unquote(cols[4]),
                    unquote(cols[5]),
                    parseDate(unquote(cols[6])),
                    unquote(cols[7])
            );
        } catch (Exception e) {
            return null;
        }
    }

    /** 完全な市区町村名（郡・政令市名 + 市区町村名） */
    public String fullName() {
        if (municipality.isEmpty()) return district;
        return district + municipality;
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        return LocalDate.parse(s);
    }

    private static String unquote(String s) {
        return s == null ? "" : s.replace("\"", "").trim();
    }

    private static String[] parseCsv(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQ && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else inQ = !inQ;
            } else if (c == ',' && !inQ) { fields.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }
}
