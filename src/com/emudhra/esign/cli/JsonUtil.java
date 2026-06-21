package com.emudhra.esign.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class JsonUtil {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    public static CliRequest parseRequest(String json) {
        return GSON.fromJson(json, CliRequest.class);
    }

    /**
     * Reads configFile from disk and copies its fields into request wherever the
     * request field is still null. The per-call request always wins on conflict.
     */
    public static void mergeConfigFile(CliRequest request) throws IOException {
        if (request.configFile == null || request.configFile.isEmpty()) return;

        String configJson = new String(Files.readAllBytes(Paths.get(request.configFile)), StandardCharsets.UTF_8);
        CliRequest config = GSON.fromJson(configJson, CliRequest.class);

        if (request.signatureContents == null) request.signatureContents = config.signatureContents;
        if (request.apiVersion == null) request.apiVersion = config.apiVersion;
        if (request.authMode == null) request.authMode = config.authMode;
        if (request.maxWaitPeriod == null) request.maxWaitPeriod = config.maxWaitPeriod;
        if (request.tempFolder == null) request.tempFolder = config.tempFolder;
        if (request.gateway == null) request.gateway = config.gateway;
        if (request.proxy == null) request.proxy = config.proxy;
    }

    public static String toJson(CliResponse response) {
        return GSON.toJson(response);
    }

    public static String errorJson(String message) {
        CliResponse r = new CliResponse();
        r.status = 0;
        r.errorMessage = message;
        return toJson(r);
    }
}
