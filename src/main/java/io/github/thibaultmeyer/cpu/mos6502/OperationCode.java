package io.github.thibaultmeyer.cpu.mos6502;

public final class OperationCode {

    public final String mnemonic;
    public final Runnable addressingMode;
    public final Runnable instruction;

    public OperationCode(final String mnemonic,
                         final Runnable addressingMode,
                         final Runnable instruction) {

        this.mnemonic = mnemonic;
        this.addressingMode = addressingMode;
        this.instruction = instruction;
    }

    @Override
    public String toString() {

        return this.mnemonic;
    }
}
