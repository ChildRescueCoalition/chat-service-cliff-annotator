package org.mediacloud.cliff.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mediacloud.cliff.ParseManager;
import org.mediacloud.cliff.extractor.EntityExtractor;
import org.mediacloud.cliff.servlet.utils.RequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * Wrapsthe CLAVIN geoparser behind some ports so we can integrate it into other workflows.
 * 
 * @author rahulb
 */
public class ParseTextServlet extends HttpServlet{
	
	private static final Logger logger = LoggerFactory.getLogger(ParseTextServlet.class);
	
    private static Gson gson = new Gson();
    
	public ParseTextServlet() {
	}


	@Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException{
	    doGet(request,response);
	}	
	
	@Override
    @SuppressWarnings("rawtypes")
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException{

        logger.debug("Text Parse Request from "+request.getRemoteAddr());
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        HashMap results = null;
        String text = request.getParameter("q");
        boolean manuallyReplaceDemonyms = RequestUtils.getReplaceAllDemonyms(request);
        Optional<String> language = RequestUtils.getLanguage(request);

        // check for invalid language argument
        if (language.isEmpty()) {
        	response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        	return;
        }
        logger.debug("q="+text);
        logger.debug("replaceAllDemonyms="+manuallyReplaceDemonyms);
        
        if(text==null){
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        } else {
            try {
                results = ParseManager.parseFromText(text,manuallyReplaceDemonyms, language.get());
            } catch(Exception e){   // try to give the user something useful
                logger.error(e.toString(), e);
            	StringWriter sw = new StringWriter();
            	PrintWriter pw = new PrintWriter(sw);
            	e.printStackTrace(pw);
            	results = ParseManager.getErrorText(sw.toString());
            }
            String jsonResults = gson.toJson(results);
            logger.debug(jsonResults);
            response.getWriter().write(jsonResults);
        }
	}
	
}
