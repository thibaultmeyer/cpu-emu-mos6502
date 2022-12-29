package io.github.thibaultmeyer.cpu.mos6502;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.MethodName.class)
final class MOS6502RegistersTest {

    @Test
    void getFlagFalse() {

        // Arrange
        final MOS6502Registers registers = new MOS6502Registers();
        registers.setFlag(MOS6502Registers.FLAG_ZERO, true);
        registers.setFlag(MOS6502Registers.FLAG_ZERO, false);

        // Act
        final boolean flag = registers.getFlag(MOS6502Registers.FLAG_ZERO);

        // Assert
        Assertions.assertFalse(flag);
    }

    @Test
    void getFlagTrue() {

        // Arrange
        final MOS6502Registers registers = new MOS6502Registers();
        registers.setFlag(MOS6502Registers.FLAG_ZERO, false);
        registers.setFlag(MOS6502Registers.FLAG_ZERO, true);

        // Act
        final boolean flag = registers.getFlag(MOS6502Registers.FLAG_ZERO);

        // Assert
        Assertions.assertTrue(flag);
    }

    @Test
    void setFlag() {

        // Arrange
        final MOS6502Registers registers = new MOS6502Registers();

        // Act
        registers.setFlag(MOS6502Registers.FLAG_ZERO, true);

        // Assert
        Assertions.assertEquals(MOS6502Registers.FLAG_ZERO, registers.status);
    }
}
