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
package scriptella.core;

import scriptella.configuration.ContentEl;
import scriptella.configuration.OnErrorEl;
import scriptella.configuration.ScriptEl;
import scriptella.spi.Connection;
import scriptella.spi.DialectIdentifier;
import scriptella.util.ExceptionUtils;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * TODO: Add documentation
 *
 * @author Fyodor Kupolov
 * @version 1.0
 */
public class ScriptExecutor extends ContentExecutor<ScriptEl> {
    private static final Logger LOG = Logger.getLogger(ScriptExecutor.class.getName());

    public ScriptExecutor(ScriptEl scriptEl) {
        super(scriptEl);
    }

    public void execute(final DynamicContext ctx) {
        Connection con = ctx.getConnection();
        ScriptEl scriptEl = getElement();

        ContentEl content = getContent(con.getDialectIdentifier());
        if (content == ContentEl.NULL_CONTENT) {
            warnEmptyContent();
            return;
        }
        boolean repeat;

        do {
            repeat = false;
            try {
                con.executeScript(content, ctx);
            } catch (Throwable t) {
                if (scriptEl.getOnerrorElements() != null) {
                    repeat = onError(t, new OnErrorHandler(scriptEl), ctx);
                } else {
                    ExceptionUtils.throwUnchecked(t);
                }
            }
        } while (repeat); //repat while onError returns retry

    }

    private void warnEmptyContent() {
        LOG.info("Script " + getLocation() + " has no supported dialects");
    }

    /**
     * Recursive on error fallback.
     *
     * @param t
     * @param errorHandler
     * @param ctx
     * @return true if script execution should be retried
     */
    private boolean onError(Throwable t, OnErrorHandler errorHandler, DynamicContext ctx) {
        OnErrorEl onErrorEl = errorHandler.onError(t);
        Connection con = ctx.getConnection();
        DialectIdentifier dialectId = con.getDialectIdentifier();
        if (onErrorEl != null && onErrorEl.getContent(dialectId) != null) { //if error handler present for this case
            ContentEl content = onErrorEl.getContent(dialectId);
            if (content == null) {
                LOG.log(Level.WARNING, "Script " + getLocation() + " failed and onError handler has no executable content: " + onErrorEl, t);
                ExceptionUtils.throwUnchecked(t);
                throw new IllegalStateException("Unexpected condition");//previous line always throws exception
            }
            LOG.log(Level.WARNING, "Script " + getLocation() + " failed. Using onError handler: " + onErrorEl, t);
            try {
                con.executeScript(content, ctx);
                return onErrorEl.isRetry();
            } catch (Exception e) {
                return onError(t, errorHandler, ctx); //calling this method again and triying to find another onerror
            }
        } else { //if no onError found - rethrow the exception
            ExceptionUtils.throwUnchecked(t);
        }
        return false;

    }


    public static ExecutableElement prepare(final ScriptEl s) {
        ExecutableElement se = new ScriptExecutor(s);
        se = StatisticInterceptor.prepare(se, s.getLocation());
        se = TxInterceptor.prepare(se, s);
        se = ConnectionInterceptor.prepare(se, s);
        se = ExceptionInterceptor.prepare(se, s.getLocation());
        se = IfInterceptor.prepare(se, s);

        return se;
    }
}