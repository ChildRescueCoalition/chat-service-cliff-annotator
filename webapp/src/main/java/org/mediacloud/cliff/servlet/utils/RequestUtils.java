package org.mediacloud.cliff.servlet.utils;

import org.mediacloud.cliff.extractor.EntityExtractor;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;

public final class RequestUtils {
    private RequestUtils() {
    }

    public static boolean getReplaceAllDemonyms(HttpServletRequest request) {
        String replaceAllDemonymsStr = request.getParameter("replaceAllDemonyms");
        return Boolean.parseBoolean(replaceAllDemonymsStr);
    }

    public static Optional<String> getLanguage(HttpServletRequest request) {
        String language = request.getParameter("language");
        if (language == null) {
            language = EntityExtractor.ENGLISH;
        }

        if (Arrays.asList(EntityExtractor.VALID_LANGUAGES).contains(language)) {
            return Optional.of(language);
        }
        return Optional.empty();
    }
}
