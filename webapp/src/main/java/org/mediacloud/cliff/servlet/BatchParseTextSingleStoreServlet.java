package org.mediacloud.cliff.servlet;

import com.crc.commons.dto.cliff.request.BatchParseTextRequest;
import com.crc.commons.dto.singlestore.SingleStoreExternalFunctionRequest;
import com.google.gson.Gson;
import org.mediacloud.cliff.servlet.service.ParseTextService;
import org.mediacloud.cliff.servlet.utils.RequestUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Wrapsthe CLAVIN geoparser behind some ports so we can integrate it into other workflows.
 *
 * @author rahulb
 */
public class BatchParseTextSingleStoreServlet extends HttpServlet {

    private final static Gson gson = new Gson();

    public BatchParseTextSingleStoreServlet() {
    }


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean manuallyReplaceDemonyms = RequestUtils.getReplaceAllDemonyms(request);

        response.setContentType("application/json");

        SingleStoreExternalFunctionRequest batchRequest = gson.fromJson(request.getReader(), SingleStoreExternalFunctionRequest.class);
        int numOfThreads = Optional.ofNullable(request.getParameter("numOfThreads")).map(Integer::parseUnsignedInt).orElse(1);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<SingleStoreExternalFunctionRequest>> violations = validator.validate(batchRequest);
            if (!violations.isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, violations.toString());
                response.getWriter().write(violations.toString());
                return;
            }
        }

        response.getWriter().write(gson.toJson(ParseTextService.parseTextInBatchesForSingleStore(batchRequest, numOfThreads, manuallyReplaceDemonyms)));
    }
}
