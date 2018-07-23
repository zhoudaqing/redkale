/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.bson;

import org.redkale.convert.*;
import static org.redkale.convert.Reader.SIGN_NULL;
import org.redkale.convert.ext.*;
import org.redkale.util.*;

/**
 * BSON数据源
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class BsonReader extends Reader {

    public static final short SIGN_OBJECTB = (short) 0xBB;

    public static final short SIGN_OBJECTE = (short) 0xEE;

    public static final byte SIGN_HASNEXT = 1;

    public static final byte SIGN_NONEXT = 0;

    public static final byte VERBOSE_NO = 1;

    public static final byte VERBOSE_YES = 2;

    protected byte typeval;  //字段的类型值  对应  BsonWriter.writeField

    protected int position = -1;

    private byte[] content;

    public BsonReader() {
    }

    public static ObjectPool<BsonReader> createPool(int max) {
        return new ObjectPool<>(max, (Object... params) -> new BsonReader(), null, (t) -> t.recycle());
    }

    public BsonReader(byte[] bytes) {
        setBytes(bytes, 0, bytes.length);
    }

    public BsonReader(byte[] bytes, int start, int len) {
        setBytes(bytes, start, len);
    }

    public final void setBytes(byte[] bytes) {
        if (bytes == null) {
            this.position = 0;
        } else {
            setBytes(bytes, 0, bytes.length);
        }
    }

    public final void setBytes(byte[] bytes, int start, int len) {
        if (bytes == null) {
            this.position = 0;
        } else {
            this.content = bytes;
            this.position = start - 1;
            //this.limit = start + len - 1;
        }
    }

    protected boolean recycle() {
        this.position = -1;
        this.typeval = 0;
        //this.limit = -1;
        this.content = null;
        return true;
    }

    public void close() {
        this.recycle();
    }

    /**
     * 跳过属性的值
     */
    @Override
    @SuppressWarnings("unchecked")
    public final void skipValue() {
        if (typeval == 0) return;
        final byte val = this.typeval;
        this.typeval = 0;
        switch (val) {
            case 1: readBoolean();
                break;
            case 2: readByte();
                break;
            case 3: readShort();
                break;
            case 4: readChar();
                break;
            case 5: readInt();
                break;
            case 6: readLong();
                break;
            case 7: readFloat();
                break;
            case 8: readDouble();
                break;
            case 9: readString();
                break;
            case 101:
                BoolArraySimpledCoder.instance.convertFrom(this);
                break;
            case 102:
                ByteArraySimpledCoder.instance.convertFrom(this);
                break;
            case 103:
                ShortArraySimpledCoder.instance.convertFrom(this);
                break;
            case 104:
                CharArraySimpledCoder.instance.convertFrom(this);
                break;
            case 105:
                IntArraySimpledCoder.instance.convertFrom(this);
                break;
            case 106:
                LongArraySimpledCoder.instance.convertFrom(this);
                break;
            case 107:
                FloatArraySimpledCoder.instance.convertFrom(this);
                break;
            case 108:
                DoubleArraySimpledCoder.instance.convertFrom(this);
                break;
            case 109:
                StringArraySimpledCoder.instance.convertFrom(this);
                break;
            case 127:
                BsonFactory.objectDecoder.convertFrom(this);
                break;
        }
    }

    @Override
    public final String readObjectB(final Class clazz) {
        this.fieldIndex = 0; //必须要重置为0
        final String newcls = readClassName();
        if (newcls != null && !newcls.isEmpty()) return newcls;
        short bt = readShort();
        if (bt == Reader.SIGN_NULL) return null;
        if (bt != SIGN_OBJECTB) {
            throw new ConvertException("a bson object must begin with " + (SIGN_OBJECTB)
                + " (position = " + position + ") but '" + currentByte() + "'");
        }
        return "";
    }

    @Override
    public final void readObjectE(final Class clazz) {
        if (readShort() != SIGN_OBJECTE) {
            throw new ConvertException("a bson object must end with " + (SIGN_OBJECTE)
                + " (position = " + position + ") but '" + currentByte() + "'");
        }
    }

    protected byte currentByte() {
        return this.content[this.position];
    }

    @Override
    public final int readMapB(DeMember member) {
        return readArrayB(member);
    }

    @Override
    public final void readMapE() {
    }

    /**
     * 判断下一个非空白字节是否为[
     *
     * @return 数组长度或SIGN_NULL
     */
    @Override
    public int readArrayB(DeMember member) {
        short bt = readShort();
        if (bt == Reader.SIGN_NULL) return bt;
        return (bt & 0xffff) << 16 | ((content[++this.position] & 0xff) << 8) | (content[++this.position] & 0xff);
    }

    @Override
    public final void readArrayE() {
    }

    /**
     * 判断下一个非空白字节是否:
     */
    @Override
    public final void readBlank() {
    }

    @Override
    public int position() {
        return this.position;
    }

    @Override
    public int readMemberContentLength(DeMember member) {
        return -1;
    }

    /**
     * 判断对象是否存在下一个属性或者数组是否存在下一个元素
     *
     * @param startPosition 起始位置
     * @param contentLength 内容大小， 不确定的传-1
     *
     * @return 是否存在
     */
    @Override
    public boolean hasNext(int startPosition, int contentLength) {
        byte b = readByte();
        if (b == SIGN_HASNEXT) return true;
        if (b != SIGN_NONEXT) throw new ConvertException("hasNext option must be (" + (SIGN_HASNEXT)
                + " or " + (SIGN_NONEXT) + ") but '" + b + "' at position(" + this.position + ")");
        return false;
    }

    @Override
    public final DeMember readFieldName(final DeMember[] members) {
        final String exceptedfield = readSmallString();
        this.typeval = readByte();
        final int len = members.length;
        if (this.fieldIndex >= len) this.fieldIndex = 0;
        for (int k = this.fieldIndex; k < len; k++) {
            if (exceptedfield.equals(members[k].getAttribute().field())) {
                this.fieldIndex = k;
                return members[k];
            }
        }
        for (int k = 0; k < this.fieldIndex; k++) {
            if (exceptedfield.equals(members[k].getAttribute().field())) {
                this.fieldIndex = k;
                return members[k];
            }
        }
        return null;
    }

    //------------------------------------------------------------
    @Override
    public boolean readBoolean() {
        return content[++this.position] == 1;
    }

    @Override
    public byte readByte() {
        return content[++this.position];
    }

    @Override
    public final byte[] readByteArray() {
        int len = readArrayB(null);
        int contentLength = -1;
        if (len == Reader.SIGN_NULL) return null;
        if (len == Reader.SIGN_NOLENBUTBYTES) {
            contentLength = readMemberContentLength(null);
            len = Reader.SIGN_NOLENGTH;
        }
        if (len == Reader.SIGN_NOLENGTH) {
            int size = 0;
            byte[] data = new byte[8];
            int startPosition = position();
            while (hasNext(startPosition, contentLength)) {
                if (size >= data.length) {
                    byte[] newdata = new byte[data.length + 4];
                    System.arraycopy(data, 0, newdata, 0, size);
                    data = newdata;
                }
                data[size++] = readByte();
            }
            readArrayE();
            byte[] newdata = new byte[size];
            System.arraycopy(data, 0, newdata, 0, size);
            return newdata;
        } else {
            byte[] values = new byte[len];
            for (int i = 0; i < values.length; i++) {
                values[i] = readByte();
            }
            readArrayE();
            return values;
        }
    }

    @Override
    public char readChar() {
        return (char) ((0xff00 & (content[++this.position] << 8)) | (0xff & content[++this.position]));
    }

    @Override
    public short readShort() {
        return (short) ((0xff00 & (content[++this.position] << 8)) | (0xff & content[++this.position]));
    }

    @Override
    public int readInt() {
        return ((content[++this.position] & 0xff) << 24) | ((content[++this.position] & 0xff) << 16)
            | ((content[++this.position] & 0xff) << 8) | (content[++this.position] & 0xff);
    }

    @Override
    public long readLong() {
        return ((((long) content[++this.position] & 0xff) << 56)
            | (((long) content[++this.position] & 0xff) << 48)
            | (((long) content[++this.position] & 0xff) << 40)
            | (((long) content[++this.position] & 0xff) << 32)
            | (((long) content[++this.position] & 0xff) << 24)
            | (((long) content[++this.position] & 0xff) << 16)
            | (((long) content[++this.position] & 0xff) << 8)
            | (((long) content[++this.position] & 0xff)));
    }

    @Override
    public final float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    @Override
    public final double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    @Override
    public final String readClassName() {
        return readSmallString();
    }

    @Override
    public String readSmallString() {
        int len = 0xff & readByte();
        if (len == 0) return "";
        String value = new String(content, ++this.position, len);
        this.position += len - 1; //上一行已经++this.position，所以此处要-1
        return value;
    }

    @Override
    public String readString() {
        int len = readInt();
        if (len == SIGN_NULL) return null;
        if (len == 0) return "";
        String value = new String(Utility.decodeUTF8(content, ++this.position, len));
        this.position += len - 1;//上一行已经++this.position，所以此处要-1
        return value;
    }

}
