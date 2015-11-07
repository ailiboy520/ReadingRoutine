package com.agenthun.readingroutine.views;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;
import java.util.Vector;

/**
 * @project ReadingRoutine
 * @authors agenthun
 * @date 15/11/5 下午7:20.
 */
public class FilePageFactory {
    private int mWidth;
    private int mHeight;

    private File file = null;

    private MappedByteBuffer mappedByteBuffer = null;
    private int mappedByteBufferLen = 0;
    private int mappedByteBufferBegin = 0;
    private int mappedByteBufferEnd = 0;

    private String CHARSET_NAME = "UTF-8";

    private Paint mPaint;

    private int fontSize = 24;
    private int textColor = Color.BLACK;
    private int backgroundColor = Color.WHITE;
    private int marginWidth = 16;
    private int marginHeight = 16;
    private Bitmap backgroundBitmap = null;

    private Vector<String> mLines = new Vector<>();
    private int mLineCount;
    private float mVisibleHeight;
    private float mVisibleWidth;

    private boolean mIsFirstPage;
    private boolean mIsLastPage;

    public FilePageFactory(int w, int h) {
        this.mWidth = w;
        this.mHeight = h;
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setTextAlign(Paint.Align.LEFT);
        mPaint.setTextSize(fontSize);
        mPaint.setColor(textColor);
        mVisibleWidth = mWidth - marginWidth * 2;
        mVisibleHeight = mHeight - marginHeight * 2;
        mLineCount = (int) (mVisibleHeight / fontSize);
    }

    public void openFile(String strFilePath) throws IOException {
        file = new File(strFilePath);
        long len = file.length();
        mappedByteBufferLen = (int) len;
        mappedByteBuffer = new RandomAccessFile(file, "r").getChannel().map(FileChannel.MapMode.READ_ONLY, 0, len);
    }

    private byte[] readParagraphBack(int position) {
        int end = position;
        int i;
        byte b0, b1;
        if (CHARSET_NAME.equals("UTF-16LE")) {
            i = end - 2;
            while (i > 0) {
                b0 = mappedByteBuffer.get(i);
                b1 = mappedByteBuffer.get(i + 1);
                if (b0 == 0x0a && b1 == 0x00 && i != end - 2) {
                    i += 2;
                    break;
                }
                i--;
            }

        } else if (CHARSET_NAME.equals("UTF-16BE")) {
            i = end - 2;
            while (i > 0) {
                b0 = mappedByteBuffer.get(i);
                b1 = mappedByteBuffer.get(i + 1);
                if (b0 == 0x00 && b1 == 0x0a && i != end - 2) {
                    i += 2;
                    break;
                }
                i--;
            }
        } else {
            i = end - 1;
            while (i > 0) {
                b0 = mappedByteBuffer.get(i);
                if (b0 == 0x0a && i != end - 1) {
                    i++;
                    break;
                }
                i--;
            }
        }
        if (i < 0) i = 0;
        int size = end - i;
        byte[] buf = new byte[size];
        for (int j = 0; j < size; j++) {
            buf[j] = mappedByteBuffer.get(i + j);
        }
        return buf;
    }

    private byte[] readParagraphForward(int position) {
        int begin = position;
        int i = begin;
        byte b0, b1;

        if (CHARSET_NAME.equals("UTF-16LE")) {
            while (i < mappedByteBufferLen - 1) {
                b0 = mappedByteBuffer.get(i++);
                b1 = mappedByteBuffer.get(i++);
                if (b0 == 0x0a && b1 == 0x00) {
                    break;
                }
            }
        } else if (CHARSET_NAME.equals("UTF-16BE")) {
            while (i < mappedByteBufferLen - 1) {
                b0 = mappedByteBuffer.get(i++);
                b1 = mappedByteBuffer.get(i++);
                if (b0 == 0x00 && b1 == 0x0a) {
                    break;
                }
            }
        } else {
            while (i < mappedByteBufferLen) {
                b0 = mappedByteBuffer.get(i++);
                if (b0 == 0x0a) {
                    break;
                }
            }
        }
        int size = i - begin;
        byte[] buf = new byte[size];
        for (i = 0; i < size; i++) {
            buf[i] = mappedByteBuffer.get(position + i);
        }
        return buf;
    }

    private Vector<String> pageDown() {
        String strParagraph = "";
        Vector<String> lines = new Vector<>();
        while (lines.size() < mLineCount && mappedByteBufferEnd < mappedByteBufferLen) {
            byte[] paraBuf = readParagraphForward(mappedByteBufferEnd);
            mappedByteBufferEnd += paraBuf.length;
            try {
                strParagraph = new String(paraBuf, CHARSET_NAME);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            String strReturn = "";
            if (strParagraph.indexOf("\r\n") != -1) {
                strReturn = "\r\n";
                strParagraph = strParagraph.replaceAll("\r\n", "");
            } else if (strParagraph.indexOf("\n") != -1) {
                strReturn = "\n";
                strParagraph = strParagraph.replaceAll("\n", "");
            }

            if (strParagraph.length() == 0) {
                lines.add(strParagraph);
            }
            while (strParagraph.length() > 0) {
                int nSize = mPaint.breakText(strParagraph, true, mVisibleWidth, null);
                lines.add(strParagraph.substring(0, nSize));
                strParagraph = strParagraph.substring(nSize);
                if (lines.size() >= mLineCount) {
                    break;
                }
            }
            if (strParagraph.length() != 0) {
                try {
                    mappedByteBufferEnd -= (strParagraph + strReturn).getBytes(CHARSET_NAME).length;
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
        return lines;
    }

    private void pageUp() {
        if (mappedByteBufferBegin < 0) mappedByteBufferBegin = 0;

        String strParagraph = "";
        Vector<String> lines = new Vector<>();
        while (lines.size() < mLineCount && mappedByteBufferBegin > 0) {
            Vector<String> paraLines = new Vector<>();
            byte[] paraBuf = readParagraphBack(mappedByteBufferBegin);
            mappedByteBufferBegin -= paraBuf.length;
            try {
                strParagraph = new String(paraBuf, CHARSET_NAME);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            strParagraph = strParagraph.replaceAll("\r\n", "");
            strParagraph = strParagraph.replaceAll("\n", "");

            if (strParagraph.length() == 0) {
                paraLines.add(strParagraph);
            }
            while (strParagraph.length() > 0) {
                int nSize = mPaint.breakText(strParagraph, true, mVisibleWidth, null);
                paraLines.add(strParagraph.substring(0, nSize));
                strParagraph = strParagraph.substring(nSize);
            }
            lines.addAll(0, paraLines);
        }
        while (lines.size() > mLineCount) {
            try {
                mappedByteBufferBegin += lines.get(0).getBytes(CHARSET_NAME).length;
                lines.remove(0);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        mappedByteBufferEnd = mappedByteBufferBegin;
        return;
    }

    public void prePage() throws IOException {
        if (mappedByteBufferBegin <= 0) {
            mappedByteBufferBegin = 0;
            mIsFirstPage = true;
            return;
        } else {
            mIsFirstPage = false;
        }
        mLines.clear();
        pageUp();
        mLines = pageDown();
    }

    public void nextPage() throws IOException {
        if (mappedByteBufferEnd >= mappedByteBufferLen) {
            mIsLastPage = true;
            return;
        } else {
            mIsLastPage = false;
        }
        mLines.clear();
        mappedByteBufferBegin = mappedByteBufferEnd;
        mLines = pageDown();
    }

    public void onDraw(Canvas canvas) {
        if (mLines.size() == 0)
            mLines = pageDown();
        if (mLines.size() > 0) {
            if (backgroundBitmap == null) {
                canvas.drawColor(backgroundColor);
            } else {
                canvas.drawBitmap(backgroundBitmap, 0, 0, null);
            }

            int h = marginHeight;
            for (String strLine : mLines) {
                h += fontSize;
                canvas.drawText(strLine, marginWidth, h, mPaint);
            }
        }

        //计算阅读%
        float fPercent = (float) (mappedByteBufferBegin * 1.0 / mappedByteBufferLen);
        DecimalFormat df = new DecimalFormat("#0.0");
        String strPercent = df.format(fPercent * 100) + "%";
        int nPercentWidth = (int) mPaint.measureText("999.9%") + 1;
        canvas.drawText(strPercent, mWidth - nPercentWidth, mHeight - 5, mPaint);
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public void setViewSize(int width, int height) {
        this.mWidth = width;
        this.mHeight = height;
    }

    public void setBackgroundBitmap(Bitmap backgroundBitmap) {
        this.backgroundBitmap = backgroundBitmap;
    }

    public boolean isFirstPage() {
        return mIsFirstPage;
    }

    public boolean isLastPage() {
        return mIsLastPage;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
        mPaint.setTextSize(fontSize);
    }

    public int getTextColor() {
        return textColor;
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;
        mPaint.setColor(textColor);
    }

    public int getMarginWidth() {
        return marginWidth;
    }

    public void setMarginWidth(int marginWidth) {
        this.marginWidth = marginWidth;
    }

    public int getMarginHeight() {
        return marginHeight;
    }

    public void setMarginHeight(int marginHeight) {
        this.marginHeight = marginHeight;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public String getCHARSET_NAME() {
        return CHARSET_NAME;
    }

    public void setCHARSET_NAME(String CHARSET_NAME) {
        this.CHARSET_NAME = CHARSET_NAME;
    }
}