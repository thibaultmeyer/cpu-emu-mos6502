package io.github.thibaultmeyer.cpu.mos6502;

public final class MOS6502Registers {

    public static final int FLAG_CARRY_BIT = 1;
    public static final int FLAG_ZERO = 1 << 1;
    public static final int FLAG_DISABLE_INTERRUPTS = 1 << 2;
    public static final int FLAG_DECIMAL_MODE = 1 << 3;
    public static final int FLAG_BREAK = 1 << 4;
    public static final int FLAG_UNUSED = 1 << 5;
    public static final int FLAG_OVERFLOW = 1 << 6;
    public static final int FLAG_NEGATIVE = 1 << 7;

    public int accumulator;
    public int x;
    public int y;
    public int stackPointer;
    public int programCounter;
    public int status;

    /**
     * Creates a new instance.
     */
    public MOS6502Registers() {

        this.accumulator = 0;
        this.x = 0;
        this.y = 0;
        this.stackPointer = 0;
        this.programCounter = 0;
        this.status = 0;
    }

    /**
     * Resets all registers.
     *
     * @param programCounter Specific value for program counter
     */
    public void reset(final int programCounter) {

        this.accumulator = 0;
        this.x = 0;
        this.y = 0;
        this.stackPointer = 0;
        this.programCounter = programCounter;
        this.status = 0;
    }

    /**
     * Gets flag's value.
     *
     * @param flag Flag
     * @return Flag's value
     */
    public boolean getFlag(final int flag) {

        return (this.status & flag) > 0;
    }

    /**
     * Sets flag's value.
     *
     * @param flag  Flag
     * @param value Flag's value
     */
    public void setFlag(final int flag, final boolean value) {

        if (value) {
            this.status |= flag;
        } else {
            this.status &= ~flag;
        }
    }

    @Override
    public String toString() {
        return "MOS6502Registers{" +
            "accumulator=" + accumulator +
            ", x=" + x +
            ", y=" + y +
            ", stackPointer=" + stackPointer +
            ", programCounter=" + programCounter +
            ", status=" + status +
            '}';
    }
}
