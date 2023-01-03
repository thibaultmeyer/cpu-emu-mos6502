package io.github.thibaultmeyer.cpu.mos6502;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MOS 6502 CPU.
 *
 * @see <a href="https://en.wikibooks.org/wiki/6502_Assembly">6502 Assembly - Wikibooks, open books for an open world</a>
 * @see <a href="https://www.pagetable.com/c64ref/6502/?tab=3">6502 CPU | Ultimate C64 Reference</a>
 * @see <a href="https://llx.com/Neil/a2/opcodes.html">The 6502/65C02/65C816 Instruction Set Decoded</a>
 * @see <a href="https://www.nesdev.org/obelisk-6502-guide/reference.html">6502 Reference</a>
 * @see <a href="https://emudev.de/nes-emulator/opcodes-and-addressing-modes-the-6502">opcodes and addressing modes – the 6502</a>
 * @see <a href="https://chubakbidpaa.com/retro/2020/12/15/6502-stack-copy.html">Explaining the Stack Through 6502 Assembly</a>
 * @see <a href="http://www.emulator101.com/6502-addressing-modes.html">Emulator 101 - 6502 Addressing Modes</a>
 * @see <a href="https://retrocomputing.stackexchange.com/questions/17888/what-is-the-mos-6502-doing-on-each-cycle-of-an-instruction">What is the MOS 6502 doing on each cycle of an instruction?</a>
 */
public final class MOS6502Processor {

    /**
     * Memory location where to retrieve the 16-bits address used as initial value for
     * the program counter (0xFFFC + 0xFFFD).
     */
    private static final int PROGRAM_COUNTER_MEMORY_LOCATION = 0xFFFC;

    /**
     * Memory location where is located the stack. In 6502 CPU, stack memory addresses
     * range is hardcoded between 0x0100 and 0x01FF.
     */
    private static final int STACK_MEMORY_LOCATION = 0x0100;

    /**
     * Operation code map. The addressing mode is directly filled in to avoid additional
     * parsing and thus save time. For more information on how to parse an opcode in its
     * binary form, go to "<a href="https://llx.com/Neil/a2/opcodes.html">The 6502/65C02
     * /65C816 Instruction Set Decoded</a>" webpage.
     */
    private final Map<Integer, OperationCode> operationCodeMap;

    private final MOS6502Registers registers;
    private final List<BusUnit> busUnitList;

    private int cycleCount;
    private int resolvedAddress;

    /**
     * Creates a new instance.
     */
    public MOS6502Processor(final Collection<BusUnit> busUnitCollection) {

        this.operationCodeMap = new HashMap<>();
        this.registers = new MOS6502Registers();
        this.busUnitList = new ArrayList<>(busUnitCollection);
        this.cycleCount = 0;

        // Load/Store
        this.operationCodeMap.put(0xAD, new OperationCode("LDA a", this::addressingModeAbsolute, this::instructionLDA));
        this.operationCodeMap.put(0xBD, new OperationCode("LDA a,x", this::addressingModeAbsoluteIndexedX, this::instructionLDA));
        this.operationCodeMap.put(0xB9, new OperationCode("LDA a,y", this::addressingModeAbsoluteIndexedY, this::instructionLDA));
        this.operationCodeMap.put(0xA9, new OperationCode("LDA #", this::addressingModeImmediate, this::instructionLDA));
        this.operationCodeMap.put(0xA5, new OperationCode("LDA zp", this::addressingModeZeroPage, this::instructionLDA));
        this.operationCodeMap.put(0xA1, new OperationCode("LDA zp,x", this::addressingModeZeroPageIndexedX, this::instructionLDA));
        this.operationCodeMap.put(0xB5, new OperationCode("LDA (zp,x)", this::addressingModeZeroPageIndexedXIndirect, this::instructionLDA));
        this.operationCodeMap.put(0xB1, new OperationCode("LDA (zp),y", this::addressingModeZeroPageIndirectIndexedY, this::instructionLDA));
        this.operationCodeMap.put(0xAE, new OperationCode("LDX a", this::addressingModeAbsolute, this::instructionLDX));
        this.operationCodeMap.put(0xBE, new OperationCode("LDX a,y", this::addressingModeAbsoluteIndexedY, this::instructionLDX));
        this.operationCodeMap.put(0xA2, new OperationCode("LDX #", this::addressingModeImmediate, this::instructionLDX));
        this.operationCodeMap.put(0xA6, new OperationCode("LDX zp", this::addressingModeZeroPage, this::instructionLDX));
        this.operationCodeMap.put(0xB6, new OperationCode("LDX zp,y", this::addressingModeZeroPageIndexedY, this::instructionLDX));
        this.operationCodeMap.put(0xAC, new OperationCode("LDY a", this::addressingModeAbsolute, this::instructionLDY));
        this.operationCodeMap.put(0xBC, new OperationCode("LDY a,x", this::addressingModeAbsoluteIndexedX, this::instructionLDY));
        this.operationCodeMap.put(0xA0, new OperationCode("LDY #", this::addressingModeImmediate, this::instructionLDY));
        this.operationCodeMap.put(0xA4, new OperationCode("LDY zp", this::addressingModeZeroPage, this::instructionLDY));
        this.operationCodeMap.put(0xB4, new OperationCode("LDY zp,x", this::addressingModeZeroPageIndexedX, this::instructionLDY));

        this.operationCodeMap.put(0x8D, new OperationCode("STA a", this::addressingModeAbsolute, this::instructionSTA));
        this.operationCodeMap.put(0x9D, new OperationCode("STA a,x", this::addressingModeAbsoluteIndexedX, this::instructionSTA));
        this.operationCodeMap.put(0x99, new OperationCode("STA a,y", this::addressingModeAbsoluteIndexedY, this::instructionSTA));
        this.operationCodeMap.put(0x85, new OperationCode("STA zp", this::addressingModeAbsolute, this::instructionSTA));
        this.operationCodeMap.put(0x81, new OperationCode("STA (zp,x)", this::addressingModeZeroPageIndexedXIndirect, this::instructionSTA));
        this.operationCodeMap.put(0x95, new OperationCode("STA zp,x", this::addressingModeZeroPageIndexedX, this::instructionSTA));
        this.operationCodeMap.put(0x91, new OperationCode("STA (zp),y", this::addressingModeZeroPageIndirectIndexedY, this::instructionSTA));
        this.operationCodeMap.put(0x8E, new OperationCode("STX a", this::addressingModeAbsolute, this::instructionSTX));
        this.operationCodeMap.put(0x86, new OperationCode("STX zp", this::addressingModeZeroPage, this::instructionSTX));
        this.operationCodeMap.put(0x96, new OperationCode("STX zp,y", this::addressingModeZeroPageIndexedY, this::instructionSTX));
        this.operationCodeMap.put(0x8C, new OperationCode("STY a", this::addressingModeAbsolute, this::instructionSTY));
        this.operationCodeMap.put(0x84, new OperationCode("STY zp", this::addressingModeZeroPage, this::instructionSTY));
        this.operationCodeMap.put(0x94, new OperationCode("STY zp,x", this::addressingModeZeroPageIndexedX, this::instructionSTY));

        // Transfer
        this.operationCodeMap.put(0xAA, new OperationCode("TAX i", this::addressingModeImplied, this::instructionTAX));
        this.operationCodeMap.put(0x8A, new OperationCode("TXA i", this::addressingModeImplied, this::instructionTXA));
        this.operationCodeMap.put(0xA8, new OperationCode("TAY i", this::addressingModeImplied, this::instructionTAY));
        this.operationCodeMap.put(0x98, new OperationCode("TYA i", this::addressingModeImplied, this::instructionTYA));
        this.operationCodeMap.put(0xBA, new OperationCode("TSX i", this::addressingModeImplied, this::instructionTSX));
        this.operationCodeMap.put(0x9A, new OperationCode("TXS i", this::addressingModeImplied, this::instructionTXS));

        // Stack
        this.operationCodeMap.put(0x48, new OperationCode("PHA i", this::addressingModeImplied, this::instructionPHA));
        this.operationCodeMap.put(0x68, new OperationCode("PLA i", this::addressingModeImplied, this::instructionPLA));
        this.operationCodeMap.put(0x08, new OperationCode("PHP i", this::addressingModeImplied, this::instructionPHP));
        this.operationCodeMap.put(0x28, new OperationCode("PLP i", this::addressingModeImplied, this::instructionPLP));

        // Shift
        this.operationCodeMap.put(0x0E, new OperationCode("ASL a", this::addressingModeAbsolute, this::instructionASL));
        this.operationCodeMap.put(0x1E, new OperationCode("ASL a,x", this::addressingModeAbsoluteIndexedX, this::instructionASL));
        this.operationCodeMap.put(0x0A, new OperationCode("ASL A", this::addressingModeAccumulator, this::instructionASL));
        this.operationCodeMap.put(0x06, new OperationCode("ASL zp", this::addressingModeZeroPage, this::instructionASL));
        this.operationCodeMap.put(0x16, new OperationCode("ASL zp,x", this::addressingModeZeroPageIndexedX, this::instructionASL));
        this.operationCodeMap.put(0x4E, new OperationCode("LSR a", this::addressingModeAbsolute, this::instructionLSR));
        this.operationCodeMap.put(0x5E, new OperationCode("LSR a,x", this::addressingModeAbsoluteIndexedX, this::instructionLSR));
        this.operationCodeMap.put(0x4A, new OperationCode("LSR A", this::addressingModeAccumulator, this::instructionLSR));
        this.operationCodeMap.put(0x46, new OperationCode("LSR zp", this::addressingModeZeroPage, this::instructionLSR));
        this.operationCodeMap.put(0x56, new OperationCode("LSR zp,x", this::addressingModeZeroPageIndexedX, this::instructionLSR));
        this.operationCodeMap.put(0x2E, new OperationCode("ROL a", this::addressingModeAbsolute, this::instructionROL));
        this.operationCodeMap.put(0x3E, new OperationCode("ROL a,x", this::addressingModeAbsoluteIndexedX, this::instructionROL));
        this.operationCodeMap.put(0x2A, new OperationCode("ROL A", this::addressingModeAccumulator, this::instructionROL));
        this.operationCodeMap.put(0x26, new OperationCode("ROL zp", this::addressingModeZeroPage, this::instructionROL));
        this.operationCodeMap.put(0x36, new OperationCode("ROL zp,x", this::addressingModeZeroPageIndexedX, this::instructionROL));
        this.operationCodeMap.put(0x6E, new OperationCode("ROR a", this::addressingModeAbsolute, this::instructionROR));
        this.operationCodeMap.put(0x7E, new OperationCode("ROR a,x", this::addressingModeAbsoluteIndexedX, this::instructionROR));
        this.operationCodeMap.put(0x6A, new OperationCode("ROR A", this::addressingModeAccumulator, this::instructionROR));
        this.operationCodeMap.put(0x66, new OperationCode("ROR zp", this::addressingModeZeroPage, this::instructionROR));
        this.operationCodeMap.put(0x76, new OperationCode("ROR zp,x", this::addressingModeZeroPageIndexedX, this::instructionROR));

        // Logic
        this.operationCodeMap.put(0x2D, new OperationCode("AND a", this::addressingModeAbsolute, this::instructionAND));
        this.operationCodeMap.put(0x3D, new OperationCode("AND a,x", this::addressingModeAbsoluteIndexedX, this::instructionAND));
        this.operationCodeMap.put(0x39, new OperationCode("AND a,y", this::addressingModeAbsoluteIndexedY, this::instructionAND));
        this.operationCodeMap.put(0x29, new OperationCode("AND #", this::addressingModeImmediate, this::instructionAND));
        this.operationCodeMap.put(0x25, new OperationCode("AND zp", this::addressingModeZeroPage, this::instructionAND));
        this.operationCodeMap.put(0x21, new OperationCode("AND (zp,x)", this::addressingModeZeroPageIndexedXIndirect, this::instructionAND));
        this.operationCodeMap.put(0x35, new OperationCode("AND zp,x", this::addressingModeZeroPageIndexedX, this::instructionAND));
        this.operationCodeMap.put(0x31, new OperationCode("AND (zp),y", this::addressingModeZeroPageIndirectIndexedY, this::instructionAND));

        this.operationCodeMap.put(0x2C, new OperationCode("BIT a", this::addressingModeAbsolute, this::instructionBIT));
        this.operationCodeMap.put(0x89, new OperationCode("BIT #", this::addressingModeImmediate, this::instructionBIT));
        this.operationCodeMap.put(0x24, new OperationCode("BIT zp", this::addressingModeZeroPage, this::instructionBIT));

        this.operationCodeMap.put(0x4D, new OperationCode("EOR a", this::addressingModeAbsolute, this::instructionEOR));
        this.operationCodeMap.put(0x5D, new OperationCode("EOR a,x", this::addressingModeAbsoluteIndexedX, this::instructionEOR));
        this.operationCodeMap.put(0x59, new OperationCode("EOR a,y", this::addressingModeAbsoluteIndexedY, this::instructionEOR));
        this.operationCodeMap.put(0x49, new OperationCode("EOR #", this::addressingModeImmediate, this::instructionEOR));
        this.operationCodeMap.put(0x45, new OperationCode("EOR zp", this::addressingModeZeroPage, this::instructionEOR));
        this.operationCodeMap.put(0x41, new OperationCode("EOR (zp,x)", this::addressingModeZeroPageIndexedXIndirect, this::instructionEOR));
        this.operationCodeMap.put(0x55, new OperationCode("EOR zp,x", this::addressingModeZeroPageIndexedX, this::instructionEOR));
        this.operationCodeMap.put(0x51, new OperationCode("EOR (zp),y", this::addressingModeZeroPageIndirectIndexedY, this::instructionEOR));

        this.operationCodeMap.put(0x0D, new OperationCode("ORA a", this::addressingModeAbsolute, this::instructionORA));
        this.operationCodeMap.put(0x1D, new OperationCode("ORA a,x", this::addressingModeAbsoluteIndexedX, this::instructionORA));
        this.operationCodeMap.put(0x19, new OperationCode("ORA a,y", this::addressingModeAbsoluteIndexedY, this::instructionORA));
        this.operationCodeMap.put(0x09, new OperationCode("ORA #", this::addressingModeImmediate, this::instructionORA));
        this.operationCodeMap.put(0x05, new OperationCode("ORA zp", this::addressingModeZeroPage, this::instructionORA));
        this.operationCodeMap.put(0x01, new OperationCode("ORA (zp,x)", this::addressingModeZeroPageIndexedXIndirect, this::instructionORA));
        this.operationCodeMap.put(0x15, new OperationCode("ORA zp,x", this::addressingModeZeroPageIndexedX, this::instructionORA));
        this.operationCodeMap.put(0x11, new OperationCode("ORA (zp),y", this::addressingModeZeroPageIndirectIndexedY, this::instructionORA));

        // Arithmetic
        this.operationCodeMap.put(0x6D, new OperationCode("ADC a", this::addressingModeAbsolute, this::instructionADC));
        this.operationCodeMap.put(0x7D, new OperationCode("ADC a,x", this::addressingModeAbsoluteIndexedX, this::instructionADC));
        this.operationCodeMap.put(0x79, new OperationCode("ADC a,y", this::addressingModeAbsoluteIndexedY, this::instructionADC));
        this.operationCodeMap.put(0x69, new OperationCode("ADC #", this::addressingModeImmediate, this::instructionADC));
        this.operationCodeMap.put(0x65, new OperationCode("ADC zp", this::addressingModeZeroPage, this::instructionADC));
        this.operationCodeMap.put(0x61, new OperationCode("ADC (zp,x)", this::addressingModeZeroPageIndexedXIndirect, this::instructionADC));
        this.operationCodeMap.put(0x75, new OperationCode("ADC zp,x", this::addressingModeZeroPageIndexedX, this::instructionADC));
        this.operationCodeMap.put(0x71, new OperationCode("ADC (zp),y", this::addressingModeZeroPageIndirectIndexedY, this::instructionADC));

        this.operationCodeMap.put(0xCD, new OperationCode("CMP a", this::addressingModeAbsolute, this::instructionCMP));
        this.operationCodeMap.put(0xDD, new OperationCode("CMP a,x", this::addressingModeAbsoluteIndexedX, this::instructionCMP));
        this.operationCodeMap.put(0xD9, new OperationCode("CMP a,y", this::addressingModeAbsoluteIndexedY, this::instructionCMP));
        this.operationCodeMap.put(0xC9, new OperationCode("CMP #", this::addressingModeImmediate, this::instructionCMP));
        this.operationCodeMap.put(0xC5, new OperationCode("CMP zp", this::addressingModeZeroPage, this::instructionCMP));
        this.operationCodeMap.put(0xC1, new OperationCode("CMP (zp,x)", this::addressingModeZeroPageIndexedXIndirect, this::instructionCMP));
        this.operationCodeMap.put(0xD5, new OperationCode("CMP zp,x", this::addressingModeZeroPageIndexedX, this::instructionCMP));
        this.operationCodeMap.put(0xD1, new OperationCode("CMP (zp),y", this::addressingModeZeroPageIndirectIndexedY, this::instructionCMP));
        this.operationCodeMap.put(0xEC, new OperationCode("CPX a", this::addressingModeAbsolute, this::instructionCPX));
        this.operationCodeMap.put(0xE0, new OperationCode("CPX #", this::addressingModeImmediate, this::instructionCPX));
        this.operationCodeMap.put(0xE4, new OperationCode("CPX zp", this::addressingModeZeroPage, this::instructionCPX));
        this.operationCodeMap.put(0xCC, new OperationCode("CPY a", this::addressingModeAbsolute, this::instructionCPY));
        this.operationCodeMap.put(0xC0, new OperationCode("CPY #", this::addressingModeImmediate, this::instructionCPY));
        this.operationCodeMap.put(0xC4, new OperationCode("CPY zp", this::addressingModeZeroPage, this::instructionCPY));

        this.operationCodeMap.put(0xED, new OperationCode("SBC a", this::addressingModeAbsolute, this::instructionSBC));
        this.operationCodeMap.put(0xFD, new OperationCode("SBC a,x", this::addressingModeAbsoluteIndexedX, this::instructionSBC));
        this.operationCodeMap.put(0xF9, new OperationCode("SBC a,y", this::addressingModeAbsoluteIndexedY, this::instructionSBC));
        this.operationCodeMap.put(0xE9, new OperationCode("SBC #", this::addressingModeImmediate, this::instructionSBC));
        this.operationCodeMap.put(0xE5, new OperationCode("SBC zp", this::addressingModeZeroPage, this::instructionSBC));
        this.operationCodeMap.put(0xE1, new OperationCode("SBC (zp,x)", this::addressingModeZeroPageIndexedXIndirect, this::instructionSBC));
        this.operationCodeMap.put(0xF5, new OperationCode("SBC zp,x", this::addressingModeZeroPageIndexedX, this::instructionSBC));
        this.operationCodeMap.put(0xF1, new OperationCode("SBC (zp),y", this::addressingModeZeroPageIndirectIndexedY, this::instructionSBC));

        // Arithmetic: Dec/Inc
        this.operationCodeMap.put(0xCE, new OperationCode("DEC a", this::addressingModeAbsolute, this::instructionDEC));
        this.operationCodeMap.put(0xDE, new OperationCode("DEC a,x", this::addressingModeAbsoluteIndexedX, this::instructionDEC));
        this.operationCodeMap.put(0xC6, new OperationCode("DEC zp", this::addressingModeZeroPage, this::instructionDEC));
        this.operationCodeMap.put(0xD6, new OperationCode("DEC zp,x", this::addressingModeZeroPageIndexedX, this::instructionDEC));
        this.operationCodeMap.put(0xCA, new OperationCode("DEX i", this::addressingModeImplied, this::instructionDEX));
        this.operationCodeMap.put(0x88, new OperationCode("DEY i", this::addressingModeImplied, this::instructionDEY));

        this.operationCodeMap.put(0xEE, new OperationCode("INC a", this::addressingModeAbsolute, this::instructionINC));
        this.operationCodeMap.put(0xFE, new OperationCode("INC a,x", this::addressingModeAbsoluteIndexedX, this::instructionINC));
        this.operationCodeMap.put(0xE6, new OperationCode("INC zp", this::addressingModeZeroPage, this::instructionINC));
        this.operationCodeMap.put(0xF6, new OperationCode("INC zp,x", this::addressingModeZeroPageIndexedX, this::instructionINC));
        this.operationCodeMap.put(0xE8, new OperationCode("INX i", this::addressingModeImplied, this::instructionINX));
        this.operationCodeMap.put(0xC8, new OperationCode("INY i", this::addressingModeImplied, this::instructionINY));

        // Control Flow
        this.operationCodeMap.put(0x00, new OperationCode("BRK i", this::addressingModeImplied, this::instructionBRK));
        this.operationCodeMap.put(0x4C, new OperationCode("JMP a", this::addressingModeAbsolute, this::instructionJMP));
        this.operationCodeMap.put(0x6C, new OperationCode("JMP (a)", this::addressingModeAbsoluteIndirect, this::instructionJMP));
        this.operationCodeMap.put(0x20, new OperationCode("JSR a", this::addressingModeAbsolute, this::instructionJSR));
        this.operationCodeMap.put(0x40, new OperationCode("RTI i", this::addressingModeImplied, this::instructionRTI));
        this.operationCodeMap.put(0x60, new OperationCode("RTS i", this::addressingModeImplied, this::instructionRTS));

        // Control Flow: Branch
        this.operationCodeMap.put(0x90, new OperationCode("BCC r", this::addressingModeRelative, this::instructionBCC));
        this.operationCodeMap.put(0xB0, new OperationCode("BCS r", this::addressingModeRelative, this::instructionBCS));
        this.operationCodeMap.put(0xF0, new OperationCode("BEQ r", this::addressingModeRelative, this::instructionBEQ));
        this.operationCodeMap.put(0xD0, new OperationCode("BNE r", this::addressingModeRelative, this::instructionBNE));
        this.operationCodeMap.put(0x10, new OperationCode("BPL r", this::addressingModeRelative, this::instructionBPL));
        this.operationCodeMap.put(0x30, new OperationCode("BMI r", this::addressingModeRelative, this::instructionBMI));
        this.operationCodeMap.put(0x50, new OperationCode("BVC r", this::addressingModeRelative, this::instructionBVC));
        this.operationCodeMap.put(0x70, new OperationCode("BVS r", this::addressingModeRelative, this::instructionBVS));

        // Flags
        this.operationCodeMap.put(0x18, new OperationCode("CLC i", this::addressingModeImplied, this::instructionCLC));
        this.operationCodeMap.put(0xD8, new OperationCode("CLD i", this::addressingModeImplied, this::instructionCLD));
        this.operationCodeMap.put(0x58, new OperationCode("CLI i", this::addressingModeImplied, this::instructionCLI));
        this.operationCodeMap.put(0xB8, new OperationCode("CLV i", this::addressingModeImplied, this::instructionCLV));
        this.operationCodeMap.put(0x38, new OperationCode("SEC i", this::addressingModeImplied, this::instructionSEC));
        this.operationCodeMap.put(0xF8, new OperationCode("SED i", this::addressingModeImplied, this::instructionSED));
        this.operationCodeMap.put(0x78, new OperationCode("SEI i", this::addressingModeImplied, this::instructionSEI));

        // NOP
        this.operationCodeMap.put(0xEA, new OperationCode("NOP i", this::addressingModeImplied, this::instructionNOP));
    }

    /**
     * Process a single clock tick.
     */
    public void clockTick() {

        if (this.cycleCount > 0) {
            // Latest operation is not yet completed
            this.cycleCount -= 1;
            return;
        }

        // Reads operation code
        final int opcode = this.readUInt8(this.registers.programCounter);
        final OperationCode operationCode = this.operationCodeMap.get(opcode);
        if (operationCode == null) {
            throw new IllegalStateException("Unknown opcode 0x" + Integer.toHexString(opcode));
        }

        // Increments program counter
        this.registers.programCounter += 1;

        // Executes instruction
        operationCode.addressingMode.run();
        operationCode.instruction.run();
        System.err.println(operationCode + " (cycle=" + this.cycleCount + ")");

        // Decrements the number of cycles remaining for this instruction
        this.cycleCount -= 1;
    }

    /**
     * Sets the CPU into initial state.
     */
    public void reset() {

        final int lsb = this.readUInt8(PROGRAM_COUNTER_MEMORY_LOCATION);
        final int msb = this.readUInt8(PROGRAM_COUNTER_MEMORY_LOCATION + 1);
        final int programCounter = (msb << 8) | lsb;

        this.reset(programCounter);
    }

    /**
     * Sets the CPU into initial state.
     */
    public void reset(final int programCounter) {

        this.registers.reset(programCounter);
        this.registers.setFlag(MOS6502Registers.FLAG_UNUSED, true);

        this.cycleCount = 7;
    }

    /**
     * Reads a single value from specific memory address.
     *
     * @param address Memory address where to read single value
     * @return Read single value
     */
    private int readUInt8(final int address) {

        for (final BusUnit busUnit : this.busUnitList) {
            if (address >= busUnit.mappingAddressMin() && address <= busUnit.mappingAddressMax()) {
                this.cycleCount += 1;
                return busUnit.read(address & 0xFFFF) & 0xFF;
            }
        }

        throw new RuntimeException("No bus unit found to read at " + address);
    }

    /**
     * Writes a single value to specific memory address.
     *
     * @param address Memory address where to write the single value
     * @param value   Value to write
     */
    private void writeUInt8(final int address, final int value) {

        for (final BusUnit busUnit : this.busUnitList) {
            if (address >= busUnit.mappingAddressMin() && address <= busUnit.mappingAddressMax()) {
                this.cycleCount += 1;
                busUnit.write(address & 0xFFFF, value & 0xFF);
                return;
            }
        }

        throw new RuntimeException("No bus unit found to write at " + address);
    }

    /**
     * Absolute: Full 16-bit address is specified.
     */
    private void addressingModeAbsolute() {

        final int lsb = this.readUInt8(this.registers.programCounter);
        this.registers.programCounter += 1;

        final int msb = this.readUInt8(this.registers.programCounter);
        this.registers.programCounter += 1;

        this.resolvedAddress = ((msb << 8) | lsb) & 0xFFFF;
    }

    /**
     * Absolute Indirect: The little-endian two-byte value stored at the specified address.
     */
    private void addressingModeAbsoluteIndirect() {

        addressingModeAbsolute();

        final int lsb = this.readUInt8(this.resolvedAddress);
        final int msb = this.readUInt8(this.resolvedAddress + 1);

        this.resolvedAddress = ((msb << 8) | lsb) & 0xFFFF;
    }

    /**
     * Absolute Indexed with X: Value in X is added to the specified 16-bit address.
     */
    private void addressingModeAbsoluteIndexedX() {

        final int lsb = this.readUInt8(this.registers.programCounter);
        this.registers.programCounter += 1;

        final int msb = this.readUInt8(this.registers.programCounter);
        this.registers.programCounter += 1;

        this.resolvedAddress = (((msb << 8) | lsb) + this.registers.x) & 0xFFFF;
    }

    /**
     * Absolute Indexed with Y: Value in Y is added to the specified 16-bit address.
     */
    private void addressingModeAbsoluteIndexedY() {

        final int lsb = this.readUInt8(this.registers.programCounter);
        this.registers.programCounter += 1;

        final int msb = this.readUInt8(this.registers.programCounter);
        this.registers.programCounter += 1;

        this.resolvedAddress = (((msb << 8) | lsb) + this.registers.x) & 0xFFFF;
    }

    /**
     * Accumulator: Accumulator is implied as the operand, so no address needs to be specified.
     */
    private void addressingModeAccumulator() {

        this.resolvedAddress = -1;
        this.cycleCount += 1;
    }

    /**
     * Immediate: Operand is used directly to perform the computation.
     */
    private void addressingModeImmediate() {

        final int address = this.registers.programCounter;
        this.registers.programCounter += 1;

        this.resolvedAddress = address & 0xFFFF;
    }

    /**
     * Implied: Operand is implied, so it does not need to be specified.
     */
    private void addressingModeImplied() {

        this.resolvedAddress = -1;
        this.cycleCount += 1;
    }

    /**
     * Relative: Offset specified is added to the current address stored in
     * the Program Counter. Offsets can range from -128 to +127.
     */
    private void addressingModeRelative() {

        int offset = this.readUInt8(this.registers.programCounter);
        this.registers.programCounter += 1;

        if (((offset >> 7) & 1) == 1) {
            offset |= 0xFF00;
        }

        this.resolvedAddress = (this.registers.programCounter + offset) & 0xFFFF;
    }

    /**
     * Zero Page Indexed with X Indirect: Value in X is added to the specified zero-page address for a sum address.
     */
    private void addressingModeZeroPageIndexedXIndirect() {

        addressingModeZeroPageIndexedX();

        final int lsb = this.readUInt8(this.resolvedAddress);
        final int msb = this.readUInt8(this.resolvedAddress + 1);

        this.resolvedAddress = ((msb << 8) | lsb) & 0xFFFF;
    }

    /**
     * Zero Page: Single byte specifies an address in the first page of memory.
     */
    private void addressingModeZeroPage() {

        final int singleByteAddress = this.readUInt8(this.registers.programCounter);
        this.registers.programCounter += 1;

        this.resolvedAddress = singleByteAddress & 0x00FF;
    }

    /**
     * Zero Page Indexed with X: Value in X is added to the specified zero-page address.
     */
    private void addressingModeZeroPageIndexedX() {

        final int singleByteAddress = this.readUInt8(this.registers.programCounter);
        this.registers.programCounter += 1;

        this.resolvedAddress = (singleByteAddress + this.registers.x) & 0x00FF;
    }

    /**
     * Zero Page Indexed with Y: Value in Y is added to the specified zero-page address.
     */
    private void addressingModeZeroPageIndexedY() {

        final int singleByteAddress = this.readUInt8(this.registers.programCounter);
        this.registers.programCounter += 1;

        this.resolvedAddress = (singleByteAddress + this.registers.y) & 0x00FF;
    }

    /**
     * Zero Page Indirect Indexed with Y: Value in Y is added to the address at the
     * little-endian address stored at the two-byte pair of the specified address
     * (LSB) and the specified address plus one (MSB).
     */
    private void addressingModeZeroPageIndirectIndexedY() {

        addressingModeZeroPage();

        final int lsb = this.readUInt8(this.resolvedAddress);
        final int msb = this.readUInt8(this.resolvedAddress + 1);

        this.resolvedAddress = (((msb << 8) | lsb) + this.registers.y) & 0xFFFF;
    }

    /**
     * Add Memory to Accumulator with Carry.
     */
    private void instructionADC() {

        final int memoryValue = this.readUInt8(this.resolvedAddress);
        final int previousOpCarry = this.registers.getFlag(MOS6502Registers.FLAG_CARRY_BIT) ? 1 : 0;
        final int result = this.registers.accumulator + memoryValue + previousOpCarry;

        this.registers.setFlag(
            MOS6502Registers.FLAG_OVERFLOW,
            (~(this.registers.accumulator ^ memoryValue) & (this.registers.accumulator ^ result) & 0x80) == 1);

        this.registers.accumulator = result & 0xFF;
        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((this.registers.accumulator >> 7) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, result == 0);
        this.registers.setFlag(MOS6502Registers.FLAG_CARRY_BIT, result > 0xFF);
    }

    /**
     * AND Memory with Accumulator.
     */
    private void instructionAND() {

        final int memoryValue = this.readUInt8(this.resolvedAddress);
        this.registers.accumulator = (this.registers.accumulator & memoryValue) & 0xFF;

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((this.registers.accumulator >> 7) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, this.registers.accumulator == 0);
    }

    /**
     * Arithmetic Shift Left One Bit.
     */
    private void instructionASL() {

        final int currentValue = this.resolvedAddress == -1
            ? this.registers.accumulator
            : this.readUInt8(this.resolvedAddress);

        final int shiftedValue = ((currentValue & 0xFFFF) << 1) & 0xFFFF;

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((shiftedValue >> 7) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, shiftedValue == 0);
        this.registers.setFlag(MOS6502Registers.FLAG_CARRY_BIT, ((currentValue >> 7) & 1) == 1);

        if (this.resolvedAddress == -1) {
            this.registers.programCounter = shiftedValue;
        } else {
            writeUInt8(this.resolvedAddress, shiftedValue);
        }
    }

    /**
     * Branch on Carry Clear (Flag {@link MOS6502Registers#FLAG_CARRY_BIT} = 0).
     */
    private void instructionBCC() {

        if (!this.registers.getFlag(MOS6502Registers.FLAG_CARRY_BIT)) {
            if ((this.resolvedAddress & 0xFF00) != (this.registers.programCounter & 0xFF00)) {
                // Page is crossed
                this.cycleCount += 2;
            } else {
                this.cycleCount += 1;
            }

            this.registers.programCounter = this.resolvedAddress;
        }
    }

    /**
     * Branch on Carry Set (Flag {@link MOS6502Registers#FLAG_CARRY_BIT} = 1).
     */
    private void instructionBCS() {

        if (this.registers.getFlag(MOS6502Registers.FLAG_CARRY_BIT)) {
            if ((this.resolvedAddress & 0xFF00) != (this.registers.programCounter & 0xFF00)) {
                // Page is crossed
                this.cycleCount += 2;
            } else {
                this.cycleCount += 1;
            }

            this.registers.programCounter = this.resolvedAddress;
        }
    }

    /**
     * Test Bits in Memory with Accumulator.
     */
    private void instructionBIT() {

        final int memoryValue = this.readUInt8(this.resolvedAddress);
        final int tmp = memoryValue & this.registers.accumulator;

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((memoryValue >> 7) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_OVERFLOW, ((memoryValue >> 6) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, tmp == 0);
    }

    /**
     * Branch on Result Zero (Flag {@link MOS6502Registers#FLAG_ZERO} = 1).
     */
    private void instructionBEQ() {

        if (this.registers.getFlag(MOS6502Registers.FLAG_ZERO)) {
            if ((this.resolvedAddress & 0xFF00) != (this.registers.programCounter & 0xFF00)) {
                // Page is crossed
                this.cycleCount += 2;
            } else {
                this.cycleCount += 1;
            }

            this.registers.programCounter = this.resolvedAddress;
        }
    }

    /**
     * Branch on Result not Zero (Flag {@link MOS6502Registers#FLAG_ZERO} = 0).
     */
    private void instructionBNE() {

        if (!this.registers.getFlag(MOS6502Registers.FLAG_ZERO)) {
            if ((this.resolvedAddress & 0xFF00) != (this.registers.programCounter & 0xFF00)) {
                // Page is crossed
                this.cycleCount += 2;
            } else {
                this.cycleCount += 1;
            }

            this.registers.programCounter = this.resolvedAddress;
        }
    }

    /**
     * Branch on Result Minus (Flag {@link MOS6502Registers#FLAG_NEGATIVE} = 1).
     */
    private void instructionBMI() {

        if (this.registers.getFlag(MOS6502Registers.FLAG_NEGATIVE)) {
            if ((this.resolvedAddress & 0xFF00) != (this.registers.programCounter & 0xFF00)) {
                // Page is crossed
                this.cycleCount += 2;
            } else {
                this.cycleCount += 1;
            }

            this.registers.programCounter = this.resolvedAddress;
        }
    }

    /**
     * Force an Interrupt.
     */
    private void instructionBRK() {

        this.registers.setFlag(MOS6502Registers.FLAG_DISABLE_INTERRUPTS, true);
        this.registers.setFlag(MOS6502Registers.FLAG_BREAK, true);

        this.writeUInt8(STACK_MEMORY_LOCATION + this.registers.stackPointer, (this.registers.programCounter >> 8) & 0xFF);
        this.registers.stackPointer -= 1;

        this.writeUInt8(STACK_MEMORY_LOCATION + this.registers.stackPointer, this.registers.programCounter & 0xFF);
        this.registers.stackPointer -= 1;

        final int lsb = this.readUInt8(PROGRAM_COUNTER_MEMORY_LOCATION);
        final int msb = this.readUInt8(PROGRAM_COUNTER_MEMORY_LOCATION + 1);
        this.registers.programCounter = (msb << 8) | lsb;

        this.registers.setFlag(MOS6502Registers.FLAG_BREAK, false);
    }

    /**
     * Branch on Overflow Clear (Flag {@link MOS6502Registers#FLAG_OVERFLOW} = 0).
     */
    private void instructionBVC() {

        if (!this.registers.getFlag(MOS6502Registers.FLAG_OVERFLOW)) {
            if ((this.resolvedAddress & 0xFF00) != (this.registers.programCounter & 0xFF00)) {
                // Page is crossed
                this.cycleCount += 2;
            } else {
                this.cycleCount += 1;
            }

            this.registers.programCounter = this.resolvedAddress;
        }
    }

    /**
     * Branch on Overflow Set (Flag {@link MOS6502Registers#FLAG_OVERFLOW} = 1).
     */
    private void instructionBVS() {

        if (this.registers.getFlag(MOS6502Registers.FLAG_OVERFLOW)) {
            if ((this.resolvedAddress & 0xFF00) != (this.registers.programCounter & 0xFF00)) {
                // Page is crossed
                this.cycleCount += 2;
            } else {
                this.cycleCount += 1;
            }

            this.registers.programCounter = this.resolvedAddress;
        }
    }

    /**
     * Branch on Result Plus (Flag {@link MOS6502Registers#FLAG_NEGATIVE} = 0).
     */
    private void instructionBPL() {

        if (!this.registers.getFlag(MOS6502Registers.FLAG_NEGATIVE)) {
            if ((this.resolvedAddress & 0xFF00) != (this.registers.programCounter & 0xFF00)) {
                // Page is crossed
                this.cycleCount += 2;
            } else {
                this.cycleCount += 1;
            }

            this.registers.programCounter = this.resolvedAddress;
        }
    }

    /**
     * Clear Carry Flag.
     */
    private void instructionCLC() {

        this.registers.setFlag(MOS6502Registers.FLAG_CARRY_BIT, false);
    }

    /**
     * Clear Decimal Mode.
     */
    private void instructionCLD() {

        this.registers.setFlag(MOS6502Registers.FLAG_DECIMAL_MODE, false);
    }

    /**
     * Clear Interrupt Disable Status.
     */
    private void instructionCLI() {

        this.registers.setFlag(MOS6502Registers.FLAG_DISABLE_INTERRUPTS, false);
    }

    /**
     * Clear Overflow Flag.
     */
    private void instructionCLV() {

        this.registers.setFlag(MOS6502Registers.FLAG_OVERFLOW, false);
    }

    /**
     * Compare Memory and Accumulator.
     */
    private void instructionCMP() {

        final int memoryValue = this.readUInt8(this.resolvedAddress);
        final int tmp = this.registers.accumulator - memoryValue;

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((tmp >> 7) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, tmp == 0);
        this.registers.setFlag(MOS6502Registers.FLAG_CARRY_BIT, memoryValue <= tmp);
    }

    /**
     * Compare Memory and Index X.
     */
    private void instructionCPX() {

        final int memoryValue = this.readUInt8(this.resolvedAddress);
        final int tmp = this.registers.x - memoryValue;

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((tmp >> 7) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, tmp == 0);
        this.registers.setFlag(MOS6502Registers.FLAG_CARRY_BIT, memoryValue <= tmp);
    }

    /**
     * Compare Memory and Index Y.
     */
    private void instructionCPY() {

        final int memoryValue = this.readUInt8(this.resolvedAddress);
        final int tmp = this.registers.y - memoryValue;

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((tmp >> 7) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, tmp == 0);
        this.registers.setFlag(MOS6502Registers.FLAG_CARRY_BIT, memoryValue <= tmp);
    }

    /**
     * Decrement Memory by One.
     */
    private void instructionDEC() {

        int memoryValue = this.readUInt8(this.resolvedAddress);
        memoryValue = (memoryValue - 1) & 0xFF;
        this.writeUInt8(memoryValue, this.resolvedAddress);

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((memoryValue >> 7) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, memoryValue == 0);
    }

    /**
     * Decrement Index X by One.
     */
    private void instructionDEX() {

        this.registers.x -= 1;

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((this.registers.x >> 7) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, this.registers.x == 0);
    }

    /**
     * Decrement Index Y by One.
     */
    private void instructionDEY() {

        this.registers.y -= 1;

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((this.registers.y >> 7) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, this.registers.y == 0);
    }

    /**
     * Exclusive-OR Memory with Accumulator.
     */
    private void instructionEOR() {

        final int memoryValue = this.readUInt8(this.resolvedAddress);

        this.registers.accumulator = (this.registers.accumulator ^ memoryValue) & 0xFF;

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((this.registers.accumulator >> 7) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, this.registers.accumulator == 0);
    }

    /**
     * Increment Memory by One.
     */
    private void instructionINC() {

        int memoryValue = this.readUInt8(this.resolvedAddress);
        memoryValue = (memoryValue + 1) & 0xFF;
        this.writeUInt8(memoryValue, this.resolvedAddress);

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((memoryValue >> 7) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, memoryValue == 0);
    }

    /**
     * Increment Index X by One.
     */
    private void instructionINX() {

        this.registers.x += 1;

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((this.registers.x >> 7) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, this.registers.x == 0);
    }

    /**
     * Increment Index Y by One.
     */
    private void instructionINY() {

        this.registers.y += 1;

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((this.registers.y >> 7) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, this.registers.y == 0);
    }

    /**
     * Jump to New Location.
     */
    private void instructionJMP() {

        this.registers.programCounter = this.resolvedAddress;
    }

    /**
     * Jump to New Location (subroutine) Saving Return Address.
     */
    private void instructionJSR() {

        this.registers.programCounter = this.registers.programCounter - 1;

        this.writeUInt8(STACK_MEMORY_LOCATION + this.registers.stackPointer, (this.registers.programCounter >> 8) & 0xFF);
        this.registers.stackPointer -= 1;

        this.writeUInt8(STACK_MEMORY_LOCATION + this.registers.stackPointer, this.registers.programCounter & 0xFF);
        this.registers.stackPointer -= 1;

        this.registers.programCounter = this.resolvedAddress;
    }

    /**
     * Load Accumulator with Memory.
     */
    private void instructionLDA() {

        this.registers.accumulator = this.readUInt8(this.resolvedAddress);

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((this.registers.accumulator >> 7) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, this.registers.accumulator == 0);
    }

    /**
     * Load Index X with Memory.
     */
    private void instructionLDX() {

        this.registers.x = this.readUInt8(this.resolvedAddress);

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((this.registers.x >> 7) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, this.registers.x == 0);
    }

    /**
     * Load Index Y with Memory.
     */
    private void instructionLDY() {

        this.registers.y = this.readUInt8(this.resolvedAddress);

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((this.registers.y >> 7) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, this.registers.y == 0);
    }

    /**
     * Logical Shift Right One Bit.
     */
    private void instructionLSR() {

        final int currentValue = this.resolvedAddress == -1
            ? this.registers.accumulator
            : this.readUInt8(this.resolvedAddress);

        final int shiftedValue = ((currentValue & 0xFFFF) >> 1) & 0xFFFF;

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, (currentValue & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, shiftedValue == 0);
        this.registers.setFlag(MOS6502Registers.FLAG_CARRY_BIT, ((shiftedValue >> 7) & 1) == 1);

        if (this.resolvedAddress == -1) {
            this.registers.programCounter = shiftedValue;
        } else {
            writeUInt8(this.resolvedAddress, shiftedValue);
        }
    }

    /**
     * No operation.
     */
    private void instructionNOP() {

        // Nothing to do
    }

    /**
     * OR Memory with Accumulator.
     */
    private void instructionORA() {

        final int memoryValue = this.readUInt8(this.resolvedAddress);
        this.registers.accumulator = (this.registers.accumulator | memoryValue) & 0xFF;

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((this.registers.accumulator >> 7) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, this.registers.accumulator == 0);
    }

    /**
     * Push Accumulator on Stack.
     */
    private void instructionPHA() {

        this.writeUInt8(STACK_MEMORY_LOCATION + this.registers.stackPointer, this.registers.accumulator);
        this.registers.stackPointer -= 1;
    }

    /**
     * Pull Accumulator from Stack.
     */
    private void instructionPLA() {

        this.registers.stackPointer += 1;
        this.registers.accumulator = this.readUInt8(STACK_MEMORY_LOCATION + this.registers.stackPointer);

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((this.registers.accumulator >> 7) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, this.registers.accumulator == 0);
    }

    /**
     * Push Processor Status on Stack.
     */
    private void instructionPHP() {

        this.writeUInt8(STACK_MEMORY_LOCATION + this.registers.stackPointer, this.registers.status);
        this.registers.stackPointer -= 1;
    }

    /**
     * Pull Processor Status from Stack.
     */
    private void instructionPLP() {

        this.registers.stackPointer += 1;
        this.registers.status = this.readUInt8(STACK_MEMORY_LOCATION + this.registers.stackPointer);
    }

    /**
     * Rotate Left One Bit.
     */
    private void instructionROL() {

        final int carryFlagValue = this.registers.getFlag(MOS6502Registers.FLAG_CARRY_BIT) ? 1 : 0;
        final int currentValue = this.resolvedAddress == -1
            ? this.registers.accumulator
            : this.readUInt8(this.resolvedAddress);

        final int shiftedValue = (((currentValue & 0xFFFF) << 1) | carryFlagValue) & 0xFFFF;

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((shiftedValue >> 6) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, shiftedValue == 0);
        this.registers.setFlag(MOS6502Registers.FLAG_CARRY_BIT, ((currentValue >> 7) & 1) == 1);

        if (this.resolvedAddress == -1) {
            this.registers.programCounter = shiftedValue;
        } else {
            writeUInt8(this.resolvedAddress, shiftedValue);
        }
    }

    /**
     * Rotate Right One Bit.
     */
    private void instructionROR() {

        final int carryFlagValue = this.registers.getFlag(MOS6502Registers.FLAG_CARRY_BIT) ? 1 : 0;
        final int currentValue = this.resolvedAddress == -1
            ? this.registers.accumulator
            : this.readUInt8(this.resolvedAddress);

        final int shiftedValue = (((carryFlagValue & 0xFFFF) << 7) | ((currentValue & 0xFFFF) >> 1)) & 0xFFFF;

        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((shiftedValue >> 6) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, shiftedValue == 0);
        this.registers.setFlag(MOS6502Registers.FLAG_CARRY_BIT, (currentValue & 1) == 1);

        if (this.resolvedAddress == -1) {
            this.registers.programCounter = shiftedValue;
        } else {
            writeUInt8(this.resolvedAddress, shiftedValue);
        }
    }

    /**
     * Return from Interrupt.
     */
    private void instructionRTI() {

        this.registers.stackPointer += 1;
        this.registers.status = this.readUInt8(STACK_MEMORY_LOCATION + this.registers.stackPointer);

        final int lsb = this.readUInt8(STACK_MEMORY_LOCATION + this.registers.stackPointer);
        this.registers.stackPointer += 1;

        final int msb = this.readUInt8(STACK_MEMORY_LOCATION + this.registers.stackPointer);
        this.registers.stackPointer += 1;

        this.registers.programCounter = (msb << 8) | lsb;
    }

    /**
     * Return from Subroutine.
     */
    private void instructionRTS() {

        final int lsb = this.readUInt8(STACK_MEMORY_LOCATION + this.registers.stackPointer);
        this.registers.stackPointer += 1;

        final int msb = this.readUInt8(STACK_MEMORY_LOCATION + this.registers.stackPointer);
        this.registers.stackPointer += 1;

        this.registers.programCounter = (msb << 8) | lsb;
        this.registers.programCounter += 1;
    }

    /**
     * Subtract Memory from Accumulator with Borrow.
     */
    private void instructionSBC() {

        // It is possible to convert the subtraction to a simple addition by simply inverting
        // the bits of the signed value to subtract.
        //
        //  3 = 00000011
        // -3 = 11111100 + 00000001 = 11111101 (or 253 because uint8 have range 0..255)
        //
        // The documentation for the SBC instruction states that the operation "1 - C" (or ~C)
        // should be performed. However, the XOR operator already does this.
        final int memoryValue = this.readUInt8(this.resolvedAddress);
        final int invertedBitMemoryValue = ((memoryValue & 0xFF) ^ 0x00FF) & 0xFF;
        final int previousOpCarry = this.registers.getFlag(MOS6502Registers.FLAG_CARRY_BIT) ? 1 : 0;

        final int result = this.registers.accumulator + invertedBitMemoryValue + previousOpCarry;

        this.registers.setFlag(
            MOS6502Registers.FLAG_OVERFLOW,
            (~(this.registers.accumulator ^ invertedBitMemoryValue) & (this.registers.accumulator ^ result) & 0x80) == 1);

        this.registers.accumulator = result & 0xFF;
        this.registers.setFlag(MOS6502Registers.FLAG_NEGATIVE, ((this.registers.accumulator >> 7) & 1) == 1);
        this.registers.setFlag(MOS6502Registers.FLAG_ZERO, result == 0);
        this.registers.setFlag(MOS6502Registers.FLAG_CARRY_BIT, result > 0xFF);
    }

    /**
     * Set Carry Flag.
     */
    private void instructionSEC() {

        this.registers.setFlag(MOS6502Registers.FLAG_CARRY_BIT, true);
    }

    /**
     * Set Decimal Mode.
     */
    private void instructionSED() {

        this.registers.setFlag(MOS6502Registers.FLAG_DECIMAL_MODE, true);
    }

    /**
     * Set Interrupt Disable Status.
     */
    private void instructionSEI() {

        this.registers.setFlag(MOS6502Registers.FLAG_DISABLE_INTERRUPTS, true);
    }

    /**
     * Store Accumulator in Memory.
     */
    private void instructionSTA() {

        this.writeUInt8(this.resolvedAddress, this.registers.accumulator);
    }

    /**
     * Store Index X in Memory.
     */
    private void instructionSTX() {

        this.writeUInt8(this.resolvedAddress, this.registers.x);
    }

    /**
     * Store Index X in Memory.
     */
    private void instructionSTY() {

        this.writeUInt8(this.resolvedAddress, this.registers.y);
    }

    /**
     * Transfer Accumulator to Index X.
     */
    private void instructionTAX() {

        this.registers.x = this.registers.accumulator;
    }

    /**
     * Transfer Accumulator to Index Y.
     */
    private void instructionTAY() {

        this.registers.y = this.registers.accumulator;
    }

    /**
     * Transfer Stack Pointer to Index X.
     */
    private void instructionTSX() {

        this.registers.x = this.registers.stackPointer;
    }

    /**
     * Transfer Index X to Accumulator.
     */
    private void instructionTXA() {

        this.registers.accumulator = this.registers.x;
    }

    /**
     * Transfer Index X to Stack Pointer.
     */
    private void instructionTXS() {

        this.registers.stackPointer = this.registers.x;
    }

    /**
     * Transfer Index Y to Accumulator.
     */
    private void instructionTYA() {

        this.registers.accumulator = this.registers.y;
    }
}
