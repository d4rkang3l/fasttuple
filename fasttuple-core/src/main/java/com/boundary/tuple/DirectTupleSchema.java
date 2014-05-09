package com.boundary.tuple;

import com.boundary.tuple.codegen.DirectTupleCodeGenerator;
import com.boundary.tuple.unsafe.Coterie;
import sun.misc.Unsafe;

import java.util.Arrays;
import java.util.Comparator;

import static com.boundary.tuple.SizeOf.sizeOf;

/**
 * Created by cliff on 5/9/14.
 */
public class DirectTupleSchema extends TupleSchema {
    // layout is the mapping from the given logical index to an offset in the tuple
    protected int[] layout;
    protected int[] widths;
    protected int byteSize;
    protected long addressOffset;
    protected boolean padToCacheLine;

    private static Unsafe unsafe = Coterie.unsafe();

    public static class Builder extends TupleSchema.Builder {
        protected boolean padding = false;

        public Builder(TupleSchema.Builder builder) {
            this.fn = builder.fn;
            this.ft = builder.ft;
            this.iface = builder.iface;
        }

        public Builder padToMachineWord(boolean padding) {
            this.padding = true;
            return this;
        }

        public DirectTupleSchema build() {
            return new DirectTupleSchema(fn.toArray(new String[fn.size()]), ft.toArray(new Class[ft.size()]), iface, padding);
        }
    }

    public DirectTupleSchema(String[] fieldNames, Class[] fieldTypes, Class iface, boolean padding) {
        super(fieldNames, fieldTypes, iface);
        int size = fieldNames.length;
        this.layout = new int[size];
        this.widths = new int[size];
        this.padToCacheLine = padding;
        generateLayout();
    }

    public long getLong(long address, int index) {
        return unsafe.getLong(address + layout[index]);
    }

    public int getInt(long address, int index) {
        return unsafe.getInt(address + layout[index]);
    }

    public short getShort(long address, int index) {
        return unsafe.getShort(address + layout[index]);
    }

    public char getChar(long address, int index) {
        return unsafe.getChar(address + layout[index]);
    }

    public byte getByte(long address, int index) {
        return unsafe.getByte(address + layout[index]);
    }

    public double getDouble(long address, int index) {
        return unsafe.getDouble(address + layout[index]);
    }

    public float getFloat(long address, int index) {
        return unsafe.getFloat(address + layout[index]);
    }

    public void setLong(long address, int index, long value) {
        unsafe.putLong(address + layout[index], value);
    }

    public void setInt(long address, int index, int value) {
        unsafe.putInt(address + layout[index], value);
    }

    public void setShort(long address, int index, short value) {
        unsafe.putShort(address + layout[index], value);
    }

    public void setChar(long address, int index, char value) {
        unsafe.putChar(address + layout[index], value);
    }

    public void setByte(long address, int index, byte value) {
        unsafe.putByte(address + layout[index], value);
    }

    public void setFloat(long address, int index, float value) {
        unsafe.putFloat(address + layout[index], value);
    }

    public void setDouble(long address, int index, double value) {
        unsafe.putDouble(address + layout[index], value);
    }

    public int[] getLayout() {
        return layout.clone();
    }

    public int getByteSize() {
        return byteSize;
    }

    public FastTuple createTuple(long address) throws Exception {
        if (clazz == null) {
            throw new IllegalStateException("generateClass must be called before createTuple");
        }
        FastTuple tuple = (FastTuple) unsafe.allocateInstance(clazz);
        unsafe.putLong(tuple, addressOffset, address);
        return tuple;
    }

    public long createRecord() {
        long address = unsafe.allocateMemory(byteSize);
        unsafe.setMemory(address, byteSize, (byte) 0);
        return address;
    }

    public long createRecordArray(long size) {
        long address = unsafe.allocateMemory(size * byteSize);
        unsafe.setMemory(address, byteSize * size, (byte)0);
//        return address;
        throw new IllegalArgumentException();
    }

    public FastTuple createTuple() throws Exception {
        long address = createRecord();
        return createTuple(address);
    }

    public void destroy(long address) {
        unsafe.freeMemory(address);
    }

    public void generateClass() throws Exception {
        if (this.clazz == null) {
            this.clazz = new DirectTupleCodeGenerator(iface, fieldNames, fieldTypes, layout).cookToClass();
            this.addressOffset = unsafe.objectFieldOffset(clazz.getField("address"));
        }
    }

    protected void generateLayout() {
        Member[] members = new Member[fieldNames.length];
        for (int i = 0; i < members.length; i++) {
            members[i] = new Member(i, sizeOf(fieldTypes[i]));
        }
        Arrays.sort(members, new Comparator<Member>() {
            @Override
            public int compare(Member o1, Member o2) {
                return o2.size - o1.size;
            }
        });
        int offset = 0;
        for (int i = 0; i < members.length; i++) {
            Member m = members[i];
            layout[m.index] = offset;
            widths[m.index] = m.size;
            offset += m.size;
        }
        int padding;
        if (padToCacheLine) {
            padding = 64 - (offset % 64);
        } else {
            padding = 8 - (offset % 8);
        }

        byteSize = offset + padding;
    }

    private static class Member {
        public int index;
        public int size;

        public Member(int index, int size) {
            this.index = index;
            this.size = size;
        }
    }
}
