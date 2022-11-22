package org.mediacloud.cliff.servlet;

import com.google.gson.Gson;
import org.mediacloud.cliff.ParseManager;
import org.mediacloud.cliff.extractor.EntityExtractor;
import org.mediacloud.cliff.servlet.dto.request.BatchParseTextRequest;
import org.mediacloud.cliff.servlet.dto.response.BatchParseTextResponse;
import org.mediacloud.cliff.servlet.dto.response.ParseTextResponse;
import org.mediacloud.cliff.servlet.utils.RequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wrapsthe CLAVIN geoparser behind some ports so we can integrate it into other workflows.
 *
 * @author rahulb
 */
public class BatchParseTextServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(BatchParseTextServlet.class);

    private static Gson gson = new Gson();

    public BatchParseTextServlet() {
    }


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean manuallyReplaceDemonyms = RequestUtils.getReplaceAllDemonyms(request);

        response.setContentType("application/json");

        BatchParseTextRequest batchRequest = gson.fromJson(request.getReader(), BatchParseTextRequest.class);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<BatchParseTextRequest>> violations = validator.validate(batchRequest);
            if (!violations.isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, violations.toString());
                response.getWriter().write(violations.toString());
                return;
            }
        }

        Collection<ParseTextResponse> responses = batchRequest.getRequests().stream()
                .map(parseTextRequest -> {
                    HashMap result;
                    try {
                        result = ParseManager.parseFromText(parseTextRequest.getText(), manuallyReplaceDemonyms, parseTextRequest.getLanguage());
                    } catch (Exception e) {   // try to give the user something useful
                        logger.warn("Error parsing text of external id {}: {}",
                                parseTextRequest.getExternalId(), parseTextRequest.getText(), e);
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        e.printStackTrace(pw);
                        result = ParseManager.getErrorText(sw.toString());
                    }

                    return ParseTextResponse.builder()
                            .externalId(parseTextRequest.getExternalId())
                            .result(result)
                            .build();
                })
                .collect(Collectors.toSet());
        response.getWriter().write(gson.toJson(new BatchParseTextResponse(responses)));
    }
}
