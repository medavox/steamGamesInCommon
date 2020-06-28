package com.medavox.util.io;

public class Bytes
{
    private static void validate(int bitNum)
    {
        if(bitNum < 0
        || bitNum > 7)
        {
            throw new IllegalArgumentException("bitNum was <0 or >7");
        }
    }
    public static boolean testBit(int bitNum, byte testee)
    {
        validate(bitNum);
        byte tester = (byte)0x01;
        tester <<= bitNum;
        return (testee & tester) != 0;
    }
    
    public static byte setBit(int bitNum, byte settee)
    {
        validate(bitNum);
        byte setter = (byte)0x01;
        setter <<= bitNum;
        return (byte)(settee | setter);
    }
    
    public static byte unsetBit(int bitNum, byte settee)
    {
        validate(bitNum);        
        byte setter = (byte)0x01;
        setter <<= bitNum;
        setter = (byte)~setter;
        return (byte)(setter & settee);
    }
}
