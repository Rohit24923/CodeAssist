package com.tyron.builder.internal.logging.text;

import com.tyron.builder.internal.logging.text.StyledTextOutput;
import com.tyron.builder.api.logging.StandardOutputListener;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Subclasses need to implement {@link #doAppend(String)}, and optionally {@link #doStyleChange(StyledTextOutput.Style)}.
 */
public abstract class AbstractStyledTextOutput implements StyledTextOutput, StandardOutputListener {
    private Style style = Style.Normal;

    @Override
    public StyledTextOutput append(char c) {
        text(String.valueOf(c));
        return this;
    }

    @Override
    public StyledTextOutput append(CharSequence csq) {
        text(csq == null ? "null" : csq);
        return this;
    }

    @Override
    public StyledTextOutput append(CharSequence csq, int start, int end) {
        text(csq == null ? "null" : csq.subSequence(start, end));
        return this;
    }

    @Override
    public StyledTextOutput format(String pattern, Object... args) {
        text(String.format(pattern, args));
        return this;
    }

    @Override
    public StyledTextOutput println(Object text) {
        text(text);
        println();
        return this;
    }

    @Override
    public StyledTextOutput formatln(String pattern, Object... args) {
        format(pattern, args);
        println();
        return this;
    }

    @Override
    public void onOutput(CharSequence output) {
        text(output);
    }

    @Override
    public StyledTextOutput println() {
        text("\n");
        return this;
    }

    @Override
    public StyledTextOutput text(Object text) {
        doAppend(text == null ? "null" : text.toString());
        return this;
    }

    @Override
    public StyledTextOutput exception(Throwable throwable) {
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);
        throwable.printStackTrace(writer);
        writer.close();
        text(out.toString());
        return this;
    }

    @Override
    public StyledTextOutput withStyle(Style style) {
        return new StyleOverrideTextOutput(style, this);
    }

    @Override
    public StyledTextOutput style(Style style) {
        if (style != this.style) {
            this.style = style;
            doStyleChange(style);
        }
        return this;
    }

    public Style getStyle() {
        return style;
    }

    protected abstract void doAppend(String text);

    protected void doStyleChange(Style style) {
    }

    private static class StyleOverrideTextOutput implements StyledTextOutput {
        private final Style style;
        private final AbstractStyledTextOutput textOutput;

        public StyleOverrideTextOutput(Style style, AbstractStyledTextOutput textOutput) {
            this.style = style;
            this.textOutput = textOutput;
        }

        @Override
        public StyledTextOutput append(char c) {
            Style original = textOutput.getStyle();
            textOutput.style(style).append(c).style(original);
            return this;
        }

        @Override
        public StyledTextOutput append(CharSequence csq) {
            Style original = textOutput.getStyle();
            textOutput.style(style).append(csq).style(original);
            return this;
        }

        @Override
        public StyledTextOutput append(CharSequence csq, int start, int end) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StyledTextOutput style(Style style) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StyledTextOutput withStyle(Style style) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StyledTextOutput text(Object text) {
            Style original = textOutput.getStyle();
            textOutput.style(style).text(text).style(original);
            return this;
        }

        @Override
        public StyledTextOutput println(Object text) {
            Style original = textOutput.getStyle();
            textOutput.style(style).text(text).style(original).println();
            return this;
        }

        @Override
        public StyledTextOutput format(String pattern, Object... args) {
            Style original = textOutput.getStyle();
            textOutput.style(style).format(pattern, args).style(original);
            return this;
        }

        @Override
        public StyledTextOutput formatln(String pattern, Object... args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StyledTextOutput println() {
            throw new UnsupportedOperationException();
        }

        @Override
        public StyledTextOutput exception(Throwable throwable) {
            throw new UnsupportedOperationException();
        }
    }
}