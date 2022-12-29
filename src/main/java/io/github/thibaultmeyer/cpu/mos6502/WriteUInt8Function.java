package io.github.thibaultmeyer.cpu.mos6502;

@FunctionalInterface
public interface WriteUInt8Function {

    void writeUInt8(final char address, final short value);
}
