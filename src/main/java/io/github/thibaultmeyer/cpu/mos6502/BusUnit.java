package io.github.thibaultmeyer.cpu.mos6502;

public interface BusUnit {

    int mappingAddressMin();

    int mappingAddressMax();

    int read(int address);

    void write(int address, int value);
}
