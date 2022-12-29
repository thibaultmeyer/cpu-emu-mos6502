package io.github.thibaultmeyer.cpu.mos6502;

@FunctionalInterface
public interface ReadUInt8Function {

    short readUInt8(final char address);
}
