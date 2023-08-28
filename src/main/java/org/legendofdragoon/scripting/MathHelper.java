package org.legendofdragoon.scripting;

public class MathHelper {
  public static int get(final byte[] data, final int offset, final int size) {
    int value = 0;

    for(int i = 0; i < size; i++) {
      value |= (data[offset + i] & 0xff) << i * 8;
    }

    return value;
  }
}
