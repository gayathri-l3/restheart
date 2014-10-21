/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.softinstigate.restheart.handlers.injectors;

import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import com.mongodb.util.JSONParseException;
import com.softinstigate.restheart.hal.Representation;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.utils.ChannelReader;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import java.util.HashSet;

/**
 *
 * @author uji
 */
public class BodyInjectorHandler extends PipedHttpHandler
{
    private final static String JSON_MEDIA_TYPE = "application/json";
    
    /**
     * Creates a new instance of BodyInjectorHandler
     *
     * @param next
     */
    public BodyInjectorHandler(PipedHttpHandler next)
    {
        super(next);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        if (context.getMethod() == RequestContext.METHOD.GET || context.getMethod() == RequestContext.METHOD.DELETE) 
        {
            next.handleRequest(exchange, context);
            return;
        }
        
        // check content type
        HeaderValues contentTypes = exchange.getRequestHeaders().get(Headers.CONTENT_TYPE);

        if (contentTypes == null || contentTypes.isEmpty() ||
                contentTypes.stream().noneMatch(ct -> ct.startsWith(Representation.HAL_JSON_MEDIA_TYPE) || ct.startsWith(JSON_MEDIA_TYPE))) // content type header can be also: Content-Type: application/json; charset=utf-8
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, "Contet-Type must be either " + Representation.HAL_JSON_MEDIA_TYPE + " or " + JSON_MEDIA_TYPE);
            return;
        }
        
        String _content = ChannelReader.read(exchange.getRequestChannel());
        
        DBObject content;

        try
        {
            content = (DBObject) JSON.parse(_content);
        }
        catch (JSONParseException ex)
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "invalid data", ex);
            return;
        }   
        
        HashSet<String> keysToRemove = new HashSet<>();
        
        // filter out reserved keys
        content.keySet().stream().filter((key) -> (key.startsWith("_"))).forEach((key) ->
        {
            keysToRemove.add(key);
        });
        
        keysToRemove.stream().map((keyToRemove) ->
        {
            content.removeField(keyToRemove);
            return keyToRemove;
        }).forEach((keyToRemove) ->
        {
            context.addWarning("the reserved field " + keyToRemove + " was filtered out from the request");
        });
        
        // inject the request content in the context
        context.setContent(content);

        next.handleRequest(exchange, context);
    }
}