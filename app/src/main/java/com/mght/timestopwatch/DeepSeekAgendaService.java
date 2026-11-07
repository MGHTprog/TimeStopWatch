package com.mght.timestopwatch;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class DeepSeekAgendaService {
    interface Callback {
        void success(Result result);
        void failure(Exception error);
    }

    static final class Result {
        final String title;
        final String date;
        final int officialStart;
        final int officialEnd;
        final List<AgendaItem> items;
        final List<String> warnings;

        Result(String title, String date, int officialStart, int officialEnd,
               List<AgendaItem> items, List<String> warnings) {
            this.title = title; this.date = date; this.officialStart = officialStart;
            this.officialEnd = officialEnd; this.items = items; this.warnings = warnings;
        }
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final String PROMPT = "你是头马会议议程表解析器。只读取图片左侧/主体议程表，不要把右侧简介、礼仪、计时规则当成议程。"
            + "输出一个JSON对象，禁止Markdown。字段：meetingTitle字符串，meetingDate为YYYY-MM-DD或空字符串，"
            + "officialStart和officialEnd为HH:mm，items数组。每个item字段为start、end(HH:mm)、"
            + "activity、role、durationMinutes(整数)、section、inferred(布尔)。"
            + "正确处理合并单元格：若一个时间段包含多个子项目，用各子项目时长顺序推导开始结束时间。"
            + "签到不计入正式会议，但仍保留在items中。章节色条不是item。无法确认的内容不要编造，role可为空。"
            + "JSON示例：{\"meetingTitle\":\"例会\",\"meetingDate\":\"\",\"officialStart\":\"19:00\","
            + "\"officialEnd\":\"21:00\",\"items\":[{\"start\":\"19:00\",\"end\":\"19:05\","
            + "\"activity\":\"开场\",\"role\":\"主席\",\"durationMinutes\":5,\"section\":\"开场\",\"inferred\":false}]}";

    private static final class EncodedImage {
        final String mimeType;
        final String base64;
        EncodedImage(String mimeType, String base64) { this.mimeType = mimeType; this.base64 = base64; }
    }

    static void parse(Context context, Uri uri, String apiKey, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try { callback.success(request(app, uri, apiKey)); }
            catch (Exception error) { callback.failure(error); }
        });
    }

    private static Result request(Context context, Uri uri, String apiKey) throws Exception {
        EncodedImage image = encodeImage(context, uri);
        JSONObject body = new JSONObject();
        body.put("model", "deepseek-flash");
        body.put("temperature", 0);
        body.put("max_tokens", 5000);
        body.put("thinking", new JSONObject().put("type", "disabled"));
        body.put("response_format", new JSONObject().put("type", "json_object"));
        JSONArray content = new JSONArray()
                .put(new JSONObject().put("type", "text").put("text", PROMPT))
                .put(new JSONObject().put("type", "image_url").put("image_url",
                        new JSONObject().put("url", "data:" + image.mimeType + ";base64," + image.base64).put("detail", "original")));
        body.put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", content)));

        Exception firstDecodeError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            if (attempt > 0) content.getJSONObject(0).put("text", PROMPT
                    + "上次返回了空内容或无效JSON。请严格按示例返回一个非空JSON对象。");
            String response = execute(body, apiKey);
            try {
                JSONObject envelope = new JSONObject(response);
                JSONObject choice = envelope.getJSONArray("choices").getJSONObject(0);
                String json = choice.getJSONObject("message").optString("content", "").trim();
                if (json.isEmpty()) throw new IllegalArgumentException("DeepSeek 返回了空的 JSON 内容");
                if (json.startsWith("```")) json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
                return decode(new JSONObject(json));
            } catch (Exception error) {
                if (attempt == 0) firstDecodeError = error;
                else throw new IllegalArgumentException("DeepSeek 两次未返回有效议程JSON："
                        + (error.getMessage() == null ? firstDecodeError.getMessage() : error.getMessage()), error);
            }
        }
        throw new IllegalStateException("DeepSeek 解析失败");
    }

    private static String execute(JSONObject body, String apiKey) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://api.deepseek.com/chat/completions").openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(90_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String response = read(stream);
        connection.disconnect();
        if (status < 200 || status >= 300) {
            String message = "DeepSeek 请求失败（HTTP " + status + "）";
            try { message += "：" + new JSONObject(response).getJSONObject("error").optString("message"); }
            catch (Exception ignored) { }
            throw new IllegalStateException(message);
        }
        return response;
    }

    static Result decode(JSONObject json) {
        ArrayList<AgendaItem> items = new ArrayList<>();
        ArrayList<String> warnings = new ArrayList<>();
        JSONArray rows = json.optJSONArray("items");
        if (rows == null) rows = new JSONArray();
        int previousEnd = -1;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            int start = parseClock(row.optString("start"));
            int end = parseClock(row.optString("end"));
            int duration = row.optInt("durationMinutes", 0);
            String activity = row.optString("activity").trim();
            if (activity.isEmpty() || start < 0 || end <= start) {
                warnings.add("第" + (i + 1) + "行时间或活动无效，已忽略");
                continue;
            }
            int actual = end - start;
            if (duration <= 0) duration = actual;
            if (Math.abs(actual - duration) > 1) {
                warnings.add(activity + "：图片时长" + duration + "分钟与时间段" + actual + "分钟不一致，按时间段处理");
                duration = actual;
            }
            if (previousEnd > start) warnings.add(activity + "：与上一环节时间重叠");
            AgendaItem item = new AgendaItem(activity, row.optString("role").trim(), duration);
            item.startMinute = start; item.endMinute = end;
            item.section = row.optString("section").trim();
            item.inferred = row.optBoolean("inferred", false);
            item.requiresCards = requiresCards(activity);
            items.add(item);
            previousEnd = Math.max(previousEnd, end);
        }
        if (items.isEmpty()) throw new IllegalArgumentException("DeepSeek 没有返回有效的议程项目");
        int officialStart = parseClock(json.optString("officialStart"));
        int officialEnd = parseClock(json.optString("officialEnd"));
        if (officialStart < 0) officialStart = items.get(0).startMinute;
        if (officialEnd <= officialStart) officialEnd = items.get(items.size() - 1).endMinute;
        return new Result(json.optString("meetingTitle"), json.optString("meetingDate"),
                officialStart, officialEnd, items, warnings);
    }

    private static int parseClock(String text) {
        if (!text.matches("(?:[01]?\\d|2[0-3]):[0-5]\\d")) return -1;
        String[] parts = text.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private static boolean requiresCards(String activity) {
        String value = activity.replaceAll("\\s+", "");
        boolean noCards = value.contains("签到") || value.contains("暖场")
                || value.contains("合影") || value.contains("小休")
                || value.contains("体验回流") || value.contains("最佳评选")
                || value.contains("角色征集");
        return !noCards;
    }

    private static EncodedImage encodeImage(Context context, Uri uri) throws Exception {
        // Preserve the original bytes (and EXIF orientation) for normal phone photos. This also
        // avoids blurring the small Chinese text by decoding and recompressing it unnecessarily.
        byte[] original;
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalArgumentException("无法读取图片");
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            original = output.toByteArray();
        }
        String mime = context.getContentResolver().getType(uri);
        if (mime == null || mime.isEmpty()) mime = sniffMime(original);
        boolean directlySupported = mime.equals("image/jpeg") || mime.equals("image/png") || mime.equals("image/webp");
        if (directlySupported && original.length <= 8 * 1024 * 1024)
            return new EncodedImage(mime, Base64.encodeToString(original, Base64.NO_WRAP));

        Bitmap bitmap;
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source source = ImageDecoder.createSource(context.getContentResolver(), uri);
            bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, ignored) -> {
                int longest = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                if (longest > 4096) {
                    float scale = 4096f / longest;
                    decoder.setTargetSize(Math.max(1, Math.round(info.getSize().getWidth() * scale)),
                            Math.max(1, Math.round(info.getSize().getHeight() * scale)));
                }
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        } else {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(original, 0, original.length, bounds);
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (longest / options.inSampleSize > 4096) options.inSampleSize *= 2;
            bitmap = BitmapFactory.decodeByteArray(original, 0, original.length, options);
        }
        if (bitmap == null) throw new IllegalArgumentException("无法读取图片");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output);
        bitmap.recycle();
        return new EncodedImage("image/jpeg", Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP));
    }

    private static String sniffMime(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8) return "image/jpeg";
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        return "application/octet-stream";
    }

    private static String read(InputStream input) throws Exception {
        if (input == null) return "";
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private DeepSeekAgendaService() { }
}
