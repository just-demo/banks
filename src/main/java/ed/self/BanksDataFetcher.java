package ed.self;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static com.google.common.io.Files.createParentDirs;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.io.FileUtils.writeStringToFile;

public class BanksDataFetcher {
    private static final String options = "" +
            "<option value=\"2018-07-01\">2 квартала 2018</option>\n" +
            "<option value=\"2018-05-14\">1 квартала 2018</option>\n" +
            "<option value=\"2018-03-07\">4 квартала 2017</option>\n" +
            "<option value=\"2017-12-07\">3 квартала 2017</option>\n" +
            "<option value=\"2017-08-18\">2 квартала 2017</option>\n" +
            "<option value=\"2017-05-21\">1 квартала 2017</option>\n" +
            "<option value=\"2017-01-01\">4 квартала 2016</option>\n" +
            "<option value=\"2016-10-01\">3 квартала 2016</option>\n" +
            "<option value=\"2016-07-01\">2 квартала 2016</option>\n" +
            "<option value=\"2016-04-01\">1 квартала 2016</option>\n" +
            "<option value=\"2016-01-01\">4 квартала 2015</option>\n" +
            "<option value=\"2015-10-01\">3 квартала 2015</option>\n" +
            "<option value=\"2015-07-01\">2 квартала 2015</option>\n" +
            "<option value=\"2015-04-01\">1 квартала 2015</option>\n" +
            "<option value=\"2015-01-01\">4 квартала 2014</option>\n" +
            "<option value=\"2014-10-01\">3 квартала 2014</option>\n" +
            "<option value=\"2014-07-01\">2 квартала 2014</option>\n" +
            "<option value=\"2014-04-01\">1 квартала 2014</option>\n" +
            "<option value=\"2014-01-01\">4 квартала 2013</option>\n" +
            "<option value=\"2013-10-01\">3 квартала 2013</option>\n" +
            "<option value=\"2013-07-01\">2 квартала 2013</option>\n" +
            "<option value=\"2013-04-01\">1 квартала 2013</option>\n" +
            "<option value=\"2013-01-01\">4 квартала 2012</option>";

    public static void main(String[] args) throws Exception {
        fetchAndSaveAll();

    }

    private static void fetchAndSaveAll() throws Exception {
        fetchAndSaveRatings();
        extractAndSaveRatings();
        fetchAndSaveBanks();
    }

    private static Map<Long, String> fetchBankNames() {
        String html = readFile(banksFile());


        // TODO: fetch from here https://minfin.com.ua/banks/all/

//        return getDates().stream()
//                .map(BanksDataFetcher::fetchBankNames)
//                .map(Map::entrySet)
//                .flatMap(Collection::stream)
//                .collect(Collectors.toMap(
//                        Map.Entry::getKey,
//                        Map.Entry::getValue,
//                        (a, b) -> {
//                            if (!Objects.equals(a, b)) {
//                                throw new RuntimeException("Different bank names: " + a + " != " + b);
//                            }
//                            return a;
//                        }
//                ));
        return null;
    }

    private static void fetchAndSaveBanks() {
        String html = readURL("https://minfin.com.ua/banks/all/");
        writeFile(banksFile(), html);
    }


    private static Map<Long, String> fetchBankNames(String date) {
        Map<Long, String> bankNames = new HashMap<>();
        String html = readFile(htmlFile(date));
        Pattern pattern = Pattern.compile("data-id=\"(.+?)\"[\\S\\s]+?<a href=\"/company/(.+?)/rating/\"><span>(.+?)</span></a>");
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String bankId = matcher.group(1);
            String bankAlias = matcher.group(2);
            String bankName = matcher.group(3);
            bankNames.put(Long.valueOf(bankId), bankName);
        }
        return bankNames;
    }

    private static void extractAndSaveRatings() throws Exception {
        String json = "{" +
                getDates().stream()
                        .map(date -> "\"" + date + "\": " + fetchRatingsJson(date).replaceAll("(\\d+):", "\"$1\":"))
                        .collect(joining(",\n")) +
                "}";
        json = json.replaceAll("'", "\"");
        json = formatJson(json);
        File outFile = jsonFile();
        createParentDirs(outFile);
        writeStringToFile(outFile, json, UTF_8);
    }

    private static String formatJson(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        mapper.enable(INDENT_OUTPUT);
        return mapper.writeValueAsString(mapper.readValue(json, Object.class));
    }

    private static String fetchRatingsJson(String date) {
        String html = readFile(htmlFile(date));
        Pattern pattern = Pattern.compile("<script>\\s*data\\s*=([^;]+);\\s*</script>");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        throw new RuntimeException("Cannot retrieve json for " + date);
    }

    private static List<String> getDates() {
        return stream(options.split("\n")).map(option -> option.split("\"")[1]).collect(toList());
    }

    private static void fetchAndSaveRatings() {
        getDates().forEach(BanksDataFetcher::fetchAndSaveRating);
    }

    private static void fetchAndSaveRating(String date) {
        //https://minfin.com.ua/img/company/58/logo/1532080477.jpg
        String content = readURL("https://minfin.com.ua/banks/rating/?date=" + date);
        File outFile = htmlFile(date);
        writeFile(outFile, content);
    }

    private static String readURL(String url) {
        try {
            HttpClient httpClient = HttpClients.createDefault();
            HttpResponse response = httpClient.execute(new HttpGet(url));
            return IOUtils.toString(response.getEntity().getContent(), UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    private static File htmlFile(String date) {
        return dataFolder().resolve("html").resolve(date + ".html").toFile();
    }

    private static File banksFile() {
        return dataFolder().resolve("html").resolve("banks.html").toFile();
    }

    private static File jsonFile() {
        return dataFolder().resolve("json").resolve("bank-rates.json").toFile();
    }

    private static Path dataFolder() {
        return Paths.get("data");
    }

    private static String readFile(File file) {
        try {
            return readFileToString(file, UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeFile(File file, String data) {
        try {
            writeStringToFile(file, data, UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
