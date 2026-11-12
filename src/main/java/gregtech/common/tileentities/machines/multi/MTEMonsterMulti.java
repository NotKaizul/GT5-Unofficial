package gregtech.common.tileentities.machines.multi;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.Muffler;
import static gregtech.api.enums.HatchElement.MultiAmpEnergy;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.casingTexturePages;
import static gregtech.api.util.GTStructureUtility.activeCoils;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofFrame;

import java.util.List;

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.misc.GTStructureChannels;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.casing.Casings;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtPlusPlus.core.material.MaterialsAlloy;

@IMetaTileEntity.SkipGenerateDescription
public class MTEMonsterMulti extends MTEEnhancedMultiBlockBase<MTEMonsterMulti>
    implements ISurvivalConstructable, ICasingTextureProvider {

    private static final String STRUCTURE_PIECE_MAIN = "main";

    private int casingAmount;

    public static final IStructureDefinition<MTEMonsterMulti> STRUCTURE_DEFINITION = StructureDefinition
        .<MTEMonsterMulti>builder()

        .addShape(
            STRUCTURE_PIECE_MAIN,
            new String[][]{
                // spotless:off
                {"     ","     ","     ","     ","     ","     ","     ","     ","     ","     ","     ","     ","     ","  C  "," C~C "," CCC "},
                {"     ","     ","     ","     ","     ","  A  ","  A  ","  A  ","  A  ","  A  ","  A  ","  A  ","  A  "," CAC "," CAC ","CCCCC"},
                {"     ","     ","     ","     ","  A  "," AAA "," AAA "," AAA "," AAA "," AAA "," AAA "," AAA "," AAA ","CAAAC","CAAAC","CCCCC"},
                {"     ","     ","     ","  A  "," AAA "," AAA "," AAA ","BAAAB","BAAAB","BAAAB","BAAAB","BAAAB","BAAAB","CAAAC","CAAAC","CCCCC"},
                {"     ","     ","  A  "," AAA "," AAA "," AAA ","BAAAB","BAAAB","BAAAB","BAAAB","BAAAB","BAAAB","BAAAB","CAAAC","CAAAC","CCCCC"},
                {"     ","  A  "," AAA "," AAA "," AAA "," AAA "," AAA "," AAA "," AAA "," AAA "," AAA "," AAA "," AAA ","CAAAC","CAAAC","CCCCC"},
                {"  A  ","  A  ","  A  ","  A  ","  A  ","  A  ","  A  ","  A  ","  A  ","  A  ","  A  ","  A  ","  A  "," CAC "," CAC ","CCCCC"},
                {"     ","     ","     ","     ","     ","     ","     ","     ","     ","     ","     ","     ","     ","  C  "," CCC "," CCC "}})
                // spotless:on
        .addElement('A', ofBlock(Blocks.wool, 10))
        .addElement('B', Casings.MagneticAnchorCasing.asElement())
        .addElement(
            'C',
            buildHatchAdder(MTEMonsterMulti.class).atLeast(Maintenance, Energy.or(MultiAmpEnergy), Muffler)
                .casingIndex(Casings.MagneticAnchorCasing.textureId)
                .hint(1)
                .buildAndChain(onElementPass(MTEMonsterMulti::onCasingAdded, Casings.MagneticAnchorCasing.asElement())))
        .build();

    public MTEMonsterMulti(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    protected MTEMonsterMulti(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEMonsterMulti(this.mName);
    }

    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        casingAmount = 0;

        if (!checkPiece(STRUCTURE_PIECE_MAIN, 2, 14, 0, errors)) return;
        checkCasingMin(errors, casingAmount, 3);

        checkHasEnergyHatch(errors);
        checkHasMaintenanceHatch(errors);
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, 2, 14, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(STRUCTURE_PIECE_MAIN, stackSize, 2, 14, 0, elementBudget, env, false, true);
    }

    @Override
    public IStructureDefinition<MTEMonsterMulti> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gt.mbtt.machine_type.large_chemical_reactor"))
            .addMarkdown(new ResourceLocation("gregtech", "large-chemical-reactor"))
            .beginStructureBlock(3, 3, 3, false)
            .addController(StatCollector.translateToLocal("gt.mbtt.structure.front_center_2nd_layer"))
            .addCasing("8-22", Casings.ChemicallyInertMachineCasing.getLocalizedName(), false)
            .addCasing("1", Casings.PTFEPipeCasing.getLocalizedName(), false)
            .addCasing("1", StatCollector.translateToLocal("GT5U.structure.heating_coil"), false)
            .addEnergyHatch("1", StatCollector.translateToLocal("gt.mbtt.structure.any_casing"), 1)
            .addMaintenanceHatch("1", StatCollector.translateToLocal("gt.mbtt.structure.any_casing"), 1)
            .addStructureInfo("Structure Info")
            .toolTipFinisher();
        return tt;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        return Textures.BlockIcons.createTextureWithCasing(
            this,
            side,
            aFacing,
            aActive,
            OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR,
            OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_GLOW,
            OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE,
            OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE_GLOW);
    }

    @Override
    public ITexture getCasingTexture() {
        return casingTexturePages[1][48];
    }

    private void onCasingAdded() {
        casingAmount++;
    }
}
