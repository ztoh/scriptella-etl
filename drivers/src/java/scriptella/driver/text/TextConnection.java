/*
 * Copyright 2006 The Scriptella Project Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package scriptella.driver.text;

import scriptella.spi.AbstractConnection;
import scriptella.spi.ConnectionParameters;
import scriptella.spi.ParametersCallback;
import scriptella.spi.ProviderException;
import scriptella.spi.QueryCallback;
import scriptella.spi.Resource;
import scriptella.util.IOUtils;

import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.text.ParseException;

/**
 * Represents a connection to a Text file.
 * <p/>
 * <p>For configuration details and examples see <a href="package-summary.html">overview page</a>.
 *
 * @author Fyodor Kupolov
 * @version 1.0
 */
public class TextConnection extends AbstractConnection {


    private URL url;
    private String encoding;
    private TextScriptExecutor out;

    /**
     * Name of the <code>encoding</code> connection property.
     * Specifies charset encoding for outputted Text files.
     */
    private static final String ENCODING = "encoding";

    /**
     * For testing purposes only.
     */
    protected TextConnection() {
    }

    public TextConnection(ConnectionParameters parameters) {
        super(Driver.DIALECT, parameters);
        try {
            url = parameters.getResolvedUrl();
        } catch (ParseException e) {
            throw new TextProviderException(e.getMessage());
        }
        try {
            encoding = parameters.getCharsetProperty(ENCODING);
        } catch (ParseException e) {
            throw new TextProviderException(e.getMessage());
        }
    }

    public void executeScript(final Resource scriptContent, final ParametersCallback parametersCallback) throws ProviderException {
        initOut();
        Reader reader = null;
        try {
            reader = scriptContent.open();
            out.execute(reader, parametersCallback);
        } catch (IOException e) {
            throw new TextProviderException("Failed to produce a text file", e);
        } finally {
            IOUtils.closeSilently(reader);
        }
    }

    /**
     * Lazily initializes script writer.
     */
    protected void initOut() {
        if (out == null) {
            try {
                out = new TextScriptExecutor(IOUtils.getWriter(IOUtils.getOutputStream(url), encoding));
            } catch (IOException e) {
                throw new TextProviderException("Unable to open file " + url + " for writing", e);
            }
        }
    }

    public void executeQuery(Resource queryContent, ParametersCallback parametersCallback, QueryCallback queryCallback) throws ProviderException {
        if (out != null) {
            throw new TextProviderException("Cannot query and update a Text file simultaneously");
        }

        String qs;
        try {
            qs = IOUtils.toString(queryContent.open());
        } catch (IOException e) {
            throw new TextProviderException("Cannot read query", e);
        }

        Reader in;
        try {
            in = IOUtils.getReader(url.openStream(), encoding);
        } catch (IOException e) {
            throw new TextProviderException("Cannot open a text file for reading", e);
        }

        TextQueryExecutor tq = null;
        try {
            tq = new TextQueryExecutor(qs, in , parametersCallback);
            tq.execute(queryCallback);
        } finally {
            if (tq!=null) {
                IOUtils.closeSilently(tq);
            } else {
                IOUtils.closeSilently(in);
            }
        }
    }

    public void close() throws ProviderException {
        IOUtils.closeSilently(out);
        out = null;
    }
}