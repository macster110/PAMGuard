package soundtrapSensor;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;

import PamView.dialog.PamDialog;
import PamView.dialog.PamGridBagContraints;

/**
 * Settings dialog for the SudSensor module.
 */
public class SudSensorDialog extends PamDialog {

    private static final long serialVersionUID = 1L;

    private SudSensorParams params;

    // Magnetometer offsets
    private JTextField tfMagOffX, tfMagOffY, tfMagOffZ;
    // Magnetometer scale factors
    private JTextField tfMagScaleX, tfMagScaleY, tfMagScaleZ;
    // Accelerometer offsets
    private JTextField tfAccelOffX, tfAccelOffY, tfAccelOffZ;
    // Fusion gains
    private JTextField tfKp, tfKi;
    // Magnetic declination
    private JTextField tfDeclination;
    // ADC scale factors
    private JTextField tfMagLsb, tfAccelLsb;
    // File continuity
    private JTextField tfMaxGapMs;

    private JButton defaultsBtn;

    private SudSensorDialog(Frame parentFrame, SudSensorControl control) {
        // Pass false – we supply our own defaults button as a popup menu
        super(parentFrame, "SudSensor Settings", false);
        this.params = control.getParams().clone();

        JPanel content = new JPanel(new BorderLayout(4, 4));
        content.add(buildMagPanel(),   BorderLayout.NORTH);
        content.add(buildAccelPanel(), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        bottom.add(buildFusionPanel(), BorderLayout.NORTH);
        content.add(bottom, BorderLayout.SOUTH);

        setDialogComponent(content);

        // Add a "Defaults ▸" button to PamDialog's button panel that shows
        // a popup menu with one item per SudSensorDefaults preset.
        addDefaultsButton();

        setHelpPoint("sensors/soundtrapSensor/docs/soundtrapSensor.html");

        populateFields();

        // In viewer mode all controls are read-only
        if (control.isViewer()) {
            content.setEnabled(false);
            setAllChildrenEnabled(content, false);
            defaultsBtn.setEnabled(false);
            getOkButton().setEnabled(false);
        }

        pack();
    }

    // -----------------------------------------------------------------------
    // Defaults popup button
    // -----------------------------------------------------------------------

    private void addDefaultsButton() {
        defaultsBtn = new JButton("Defaults \u25ba");
        defaultsBtn.setToolTipText("Load factory defaults for a specific device");

        JPopupMenu menu = new JPopupMenu();
        for (SudSensorDefaults preset : SudSensorDefaults.values()) {
            JMenuItem item = new JMenuItem(preset.getDisplayName());
            item.addActionListener(e -> applyDefaults(preset));
            menu.add(item);
        }

        defaultsBtn.addActionListener(e ->
            menu.show(defaultsBtn, 0, defaultsBtn.getHeight()));

        getButtonPanel().add(defaultsBtn);
    }

    /** Apply a preset and refresh all fields. */
    private void applyDefaults(SudSensorDefaults preset) {
        // Preserve the declination the user may have already typed
        double currentDeclination = parseField(tfDeclination, params.magneticDeclinationDeg);
        params = preset.createParams();
        params.magneticDeclinationDeg = currentDeclination;
        populateFields();
    }

    // -----------------------------------------------------------------------
    // Panel builders
    // -----------------------------------------------------------------------

    private JPanel buildMagPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Magnetometer Calibration"));
        GridBagConstraints c = new PamGridBagContraints();

        addRow(p, c, "Hard-iron offset X (µT):",  tfMagOffX   = new JTextField(8));
        addRow(p, c, "Hard-iron offset Y (µT):",  tfMagOffY   = new JTextField(8));
        addRow(p, c, "Hard-iron offset Z (µT):",  tfMagOffZ   = new JTextField(8));
        addRow(p, c, "Scale factor X:",            tfMagScaleX = new JTextField(8));
        addRow(p, c, "Scale factor Y:",            tfMagScaleY = new JTextField(8));
        addRow(p, c, "Scale factor Z:",            tfMagScaleZ = new JTextField(8));
        addRow(p, c, "LSB \u2192 \u00b5T factor:", tfMagLsb   = new JTextField(8));
        return p;
    }

    private JPanel buildAccelPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Accelerometer Calibration"));
        GridBagConstraints c = new PamGridBagContraints();

        addRow(p, c, "Offset X:",              tfAccelOffX = new JTextField(8));
        addRow(p, c, "Offset Y:",              tfAccelOffY = new JTextField(8));
        addRow(p, c, "Offset Z:",              tfAccelOffZ = new JTextField(8));
        addRow(p, c, "LSB \u2192 m/s\u00b2 factor:", tfAccelLsb = new JTextField(8));
        return p;
    }

    private JPanel buildFusionPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Sensor Fusion (Mahony Filter)"));
        GridBagConstraints c = new PamGridBagContraints();

        addRow(p, c, "Proportional gain Kp:",         tfKp          = new JTextField(8));
        addRow(p, c, "Integral gain Ki:",              tfKi          = new JTextField(8));
        addRow(p, c, "Magnetic declination (\u00b0, +E):", tfDeclination = new JTextField(8));
        addRow(p, c, "Max contiguous gap (ms):",      tfMaxGapMs    = new JTextField(8));
        return p;
    }

    private void addRow(JPanel panel, GridBagConstraints c, String label, JTextField field) {
        c.gridx = 0;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(2, 4, 2, 4);
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.WEST;
        panel.add(field, c);
        c.gridy++;
    }

    // -----------------------------------------------------------------------
    // Population / reading
    // -----------------------------------------------------------------------

    private void populateFields() {
        tfMagOffX.setText(df(params.magOffsetX));
        tfMagOffY.setText(df(params.magOffsetY));
        tfMagOffZ.setText(df(params.magOffsetZ));
        tfMagScaleX.setText(df(params.magScaleX));
        tfMagScaleY.setText(df(params.magScaleY));
        tfMagScaleZ.setText(df(params.magScaleZ));
        tfMagLsb.setText(df(params.magLsbToMicroTesla));

        tfAccelOffX.setText(df(params.accelOffsetX));
        tfAccelOffY.setText(df(params.accelOffsetY));
        tfAccelOffZ.setText(df(params.accelOffsetZ));
        tfAccelLsb.setText(df(params.accelLsbToMsq));

        tfKp.setText(df(params.fusionKp));
        tfKi.setText(df(params.fusionKi));
        tfDeclination.setText(df(params.magneticDeclinationDeg));
        tfMaxGapMs.setText(String.valueOf(params.maxContiguousGapMs));
    }

    private String df(double v) { return String.valueOf(v); }

    private double parseField(JTextField tf, double defaultValue) {
        try {
            return Double.parseDouble(tf.getText().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean getParams() {
        params.magOffsetX = parseField(tfMagOffX,   params.magOffsetX);
        params.magOffsetY = parseField(tfMagOffY,   params.magOffsetY);
        params.magOffsetZ = parseField(tfMagOffZ,   params.magOffsetZ);
        params.magScaleX  = parseField(tfMagScaleX, params.magScaleX);
        params.magScaleY  = parseField(tfMagScaleY, params.magScaleY);
        params.magScaleZ  = parseField(tfMagScaleZ, params.magScaleZ);
        params.magLsbToMicroTesla = parseField(tfMagLsb, params.magLsbToMicroTesla);

        params.accelOffsetX  = parseField(tfAccelOffX,  params.accelOffsetX);
        params.accelOffsetY  = parseField(tfAccelOffY,  params.accelOffsetY);
        params.accelOffsetZ  = parseField(tfAccelOffZ,  params.accelOffsetZ);
        params.accelLsbToMsq = parseField(tfAccelLsb,   params.accelLsbToMsq);

        params.fusionKp = parseField(tfKp, params.fusionKp);
        params.fusionKi = parseField(tfKi, params.fusionKi);
        params.magneticDeclinationDeg = parseField(tfDeclination, params.magneticDeclinationDeg);
        try {
            params.maxContiguousGapMs = Long.parseLong(tfMaxGapMs.getText().trim());
        } catch (NumberFormatException e) {
            // keep existing value
        }
        return true;
    }

    /** Recursively enable or disable all child components of a container. */
    private void setAllChildrenEnabled(java.awt.Container container, boolean enabled) {
        for (java.awt.Component c : container.getComponents()) {
            c.setEnabled(enabled);
            if (c instanceof java.awt.Container) {
                setAllChildrenEnabled((java.awt.Container) c, enabled);
            }
        }
    }

    @Override
    public void cancelButtonPressed() {
        params = null;
    }

    @Override
    public void restoreDefaultSettings() {
        // Not used – defaults are applied via the popup menu
    }

    // -----------------------------------------------------------------------
    // Static factory
    // -----------------------------------------------------------------------

    public static SudSensorParams showDialog(Frame parentFrame, SudSensorControl control) {
        SudSensorDialog dlg = new SudSensorDialog(parentFrame, control);
        dlg.setVisible(true);
        return dlg.params;
    }
}