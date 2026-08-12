package org.unlaxer.municipality;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
    /**
     * このレコードが、対象自治体（lgCode）自身の廃止・合併による消滅を表すかどうかを返す。
     *
     * <p>以下のいずれかのパターンに一致する場合に廃止とみなす。</p>
     * <ul>
     *   <li>{@code (lgCode)が...に編入} — 他市区町村に吸収合併された</li>
     *   <li>{@code (lgCode)の...への政令指定都市施行/移行} — 政令市化に伴い旧コードが廃止された</li>
     *   <li>{@code (lgCode)の廃止} — 直接廃止された</li>
     * </ul>
     *
     * @return このレコードの lgCode が廃止されたことを示す場合 {@code true}
     */
    public boolean isAbolished() {
        String code = lgCode();
        String r = reason();
        if (Pattern.compile("\\(" + Pattern.quote(code) + "\\)(?:が|は).*?に編入",
                Pattern.DOTALL).matcher(r).find()) {
            return true;
        }
        if (Pattern.compile("\\(" + Pattern.quote(code) + "\\)の.*?への政令指定都市(?:施行|移行)",
                Pattern.DOTALL).matcher(r).find()) {
            return true;
        }
        if (Pattern.compile("\\(" + Pattern.quote(code) + "\\)の廃止").matcher(r).find()) {
            return true;
        }
        return false;
    }

    /**
     * CSV 行をパースして {@link MunicipalityChange} を返す。
     *
     * @param line CSV の1行
     * @param lineNumber 1始まりの行番号（ログ用）
     * @return パース成功時は非 null、列数不足時は {@code null}
     * @throws CsvParseException パースは成功したが値の変換に失敗した場合
     */
    static MunicipalityChange fromCsvLine(String line, int lineNumber) throws CsvParseException {
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
            throw new CsvParseException(lineNumber, line, e);
        }
    }

    /** 後方互換用のシグネチャ（行番号なし）。内部テストから呼ばれる。 */
    static MunicipalityChange fromCsvLine(String line) {
        try {
            return fromCsvLine(line, -1);
        } catch (CsvParseException e) {
            return null;
        }
    }

    /** CSV パース時の変換エラーを表す例外。 */
    static final class CsvParseException extends Exception {
        final int lineNumber;
        final String rawLine;

        CsvParseException(int lineNumber, String rawLine, Throwable cause) {
            super("CSV parse error at line " + lineNumber + ": " + cause.getMessage(), cause);
            this.lineNumber = lineNumber;
            this.rawLine = rawLine;
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

    /**
     * CSV レコードの引用符が閉じていない（＝レコードが次行に続く）かを返す。
     * {@code ""} は引用符のエスケープとして扱う。
     */
    static boolean hasUnclosedQuote(CharSequence s) {
        boolean inQ = false;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '"') continue;
            if (inQ && i + 1 < s.length() && s.charAt(i + 1) == '"') { i++; continue; }
            inQ = !inQ;
        }
        return inQ;
    }
}
