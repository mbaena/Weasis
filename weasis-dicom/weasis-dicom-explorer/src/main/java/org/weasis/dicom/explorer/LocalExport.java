/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.explorer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import javax.media.jai.PlanarImage;
import javax.media.jai.operator.SubsampleAverageDescriptor;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.media.DicomDirWriter;
import org.dcm4che3.media.RecordType;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.FileFormatFilter;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.api.util.StringUtil.Suffix;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.serialize.XmlSerializer;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.FileExtractor;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.internal.Activator;
import org.weasis.dicom.explorer.pr.PrSerializer;

@SuppressWarnings("serial")
public class LocalExport extends AbstractItemDialogPage implements ExportDicom {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalExport.class);

    public static final String LAST_DIR = "lastExportDir";//$NON-NLS-1$
    public static final String INC_DICOMDIR = "exp.include.dicomdir";//$NON-NLS-1$
    public static final String KEEP_INFO_DIR = "exp.keep.dir.name";//$NON-NLS-1$
    public static final String IMG_QUALITY = "exp.img.quality";//$NON-NLS-1$
    public static final String HEIGHT_BITS = "exp.8bis";//$NON-NLS-1$
    public static final String CD_COMPATIBLE = "exp.cd";//$NON-NLS-1$

    public static final String[] EXPORT_FORMAT = { "DICOM", "DICOM ZIP", "JPEG", "PNG", "TIFF" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

    private final DicomModel dicomModel;
    private JLabel lblImportAFolder;
    private File outputFolder;
    private JPanel panel;
    private final ExportTree exportTree;

    private JComboBox<String> comboBoxImgFormat;
    private JButton btnNewButton;
    private JCheckBox chckbxGraphics;

    public LocalExport(DicomModel dicomModel, CheckTreeModel treeModel) {
        super(Messages.getString("LocalExport.local_dev")); //$NON-NLS-1$
        this.dicomModel = dicomModel;
        this.exportTree = new ExportTree(treeModel);
        setComponentPosition(0);
        initGUI();
    }

    public void initGUI() {
        setLayout(new BorderLayout());
        panel = new JPanel();
        FlowLayout flowLayout = (FlowLayout) panel.getLayout();
        flowLayout.setAlignment(FlowLayout.LEFT);

        lblImportAFolder = new JLabel(Messages.getString("LocalExport.exp") + StringUtil.COLON); //$NON-NLS-1$
        panel.add(lblImportAFolder);

        comboBoxImgFormat = new JComboBox<>(new DefaultComboBoxModel<>(EXPORT_FORMAT));
        panel.add(comboBoxImgFormat);

        add(panel, BorderLayout.NORTH);

        btnNewButton = new JButton(Messages.getString("LocalExport.options")); //$NON-NLS-1$
        btnNewButton.addActionListener(e -> showExportingOptions());
        panel.add(btnNewButton);

        chckbxGraphics = new JCheckBox(Messages.getString("LocalExport.graphics"), true); //$NON-NLS-1$

        panel.add(chckbxGraphics);
        add(exportTree, BorderLayout.CENTER);
    }

    protected void showExportingOptions() {
        Properties pref = Activator.IMPORT_EXPORT_PERSISTENCE;
        final JCheckBox boxKeepNames = new JCheckBox(Messages.getString("LocalExport.keep_dir"), //$NON-NLS-1$
            Boolean.valueOf(pref.getProperty(KEEP_INFO_DIR, "true"))); //$NON-NLS-1$

        Object seltected = comboBoxImgFormat.getSelectedItem();
        if (EXPORT_FORMAT[0].equals(seltected)) {
            final JCheckBox box1 = new JCheckBox(Messages.getString("LocalExport.inc_dicomdir"), //$NON-NLS-1$
                Boolean.valueOf(pref.getProperty(INC_DICOMDIR, Boolean.TRUE.toString())));
            final JCheckBox box2 = new JCheckBox(Messages.getString("LocalExport.cd_folders"), //$NON-NLS-1$
                Boolean.valueOf(pref.getProperty(CD_COMPATIBLE, Boolean.FALSE.toString())));
            box2.setEnabled(box1.isSelected());
            boxKeepNames.setEnabled(!box1.isSelected());
            box1.addActionListener(e -> {
                boxKeepNames.setEnabled(!box1.isSelected());
                box2.setEnabled(box1.isSelected());
            });

            Object[] options = { box1, box2, boxKeepNames };
            int response = JOptionPane.showOptionDialog(this, options, Messages.getString("LocalExport.export_message"), //$NON-NLS-1$
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null);
            if (response == JOptionPane.OK_OPTION) {
                pref.setProperty(INC_DICOMDIR, String.valueOf(box1.isSelected()));
                pref.setProperty(KEEP_INFO_DIR, String.valueOf(boxKeepNames.isSelected()));
                pref.setProperty(CD_COMPATIBLE, String.valueOf(box2.isSelected()));
            }
        } else if (EXPORT_FORMAT[1].equals(seltected)) {
            // No option
        } else if (EXPORT_FORMAT[2].equals(seltected)) {
            final JSlider slider = new JSlider(0, 100, StringUtil.getInteger(pref.getProperty(IMG_QUALITY, null), 80));

            final JPanel palenSlider1 = new JPanel();
            palenSlider1.setLayout(new BoxLayout(palenSlider1, BoxLayout.Y_AXIS));
            palenSlider1.setBorder(new TitledBorder(
                Messages.getString("LocalExport.jpeg_quality") + StringUtil.COLON_AND_SPACE + slider.getValue())); //$NON-NLS-1$

            slider.setPaintTicks(true);
            slider.setSnapToTicks(false);
            slider.setMajorTickSpacing(10);
            JMVUtils.setPreferredWidth(slider, 145, 145);
            palenSlider1.add(slider);
            slider.addChangeListener(e -> {
                JSlider source = (JSlider) e.getSource();
                ((TitledBorder) palenSlider1.getBorder())
                    .setTitle(Messages.getString("LocalExport.jpeg_quality") + source.getValue()); //$NON-NLS-1$
                palenSlider1.repaint();
            });

            Object[] options = { palenSlider1, boxKeepNames };
            int response = JOptionPane.showOptionDialog(this, options, Messages.getString("LocalExport.export_message"), //$NON-NLS-1$
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null);
            if (response == JOptionPane.OK_OPTION) {
                pref.setProperty(IMG_QUALITY, String.valueOf(slider.getValue()));
                pref.setProperty(KEEP_INFO_DIR, String.valueOf(boxKeepNames.isSelected()));
            }
        } else if (EXPORT_FORMAT[3].equals(seltected)) {
            Object[] options = { boxKeepNames };
            int response = JOptionPane.showOptionDialog(this, options, Messages.getString("LocalExport.export_message"), //$NON-NLS-1$
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null);
            if (response == JOptionPane.OK_OPTION) {
                pref.setProperty(KEEP_INFO_DIR, String.valueOf(boxKeepNames.isSelected()));
            }
        } else if (EXPORT_FORMAT[4].equals(seltected)) {
            final JCheckBox box1 = new JCheckBox(Messages.getString("LocalExport.tiff_sup_8bits"), //$NON-NLS-1$
                Boolean.valueOf(pref.getProperty(HEIGHT_BITS, "false"))); //$NON-NLS-1$
            Object[] options = { box1, boxKeepNames };
            int response = JOptionPane.showOptionDialog(this, options, Messages.getString("LocalExport.export_message"), //$NON-NLS-1$
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null);
            if (response == JOptionPane.OK_OPTION) {
                pref.setProperty(HEIGHT_BITS, String.valueOf(box1.isSelected()));
                pref.setProperty(KEEP_INFO_DIR, String.valueOf(boxKeepNames.isSelected()));
            }
        }
    }

    public void browseImgFile(String format) {
        String targetDirectoryPath = Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(LAST_DIR, "");//$NON-NLS-1$

        boolean isSaveFileMode = EXPORT_FORMAT[1].equals(format);

        JFileChooser fileChooser = new JFileChooser(targetDirectoryPath);

        if (isSaveFileMode) {
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setAcceptAllFileFilterUsed(false);
            FileFormatFilter filter = new FileFormatFilter("zip", "ZIP"); //$NON-NLS-1$ //$NON-NLS-2$
            fileChooser.addChoosableFileFilter(filter);
            fileChooser.setFileFilter(filter);

        } else {
            /**
             * Idea is to show all the files in the directories to give the user some context, but only directories
             * should be accepted as selections. As the effect is L&F dependent, consider using DIRECTORIES_ONLY on
             * platforms that already meet your UI requirements. Empirically, it's platform-dependent, with files
             * appearing gray in all supported L&Fs on Mac OS X. <br>
             * Disabling file selection may be annoying. A solution is just to allow the user to select either a file or
             * a directory and if the user select a file just use the directory where that file is located.
             */

            if (System.getProperty("os.name").startsWith("Mac OS X")) { //$NON-NLS-1$ //$NON-NLS-2$
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            } else {
                fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            }

        }

        fileChooser.setMultiSelectionEnabled(false);

        // Set default selection name to enable save button
        if (StringUtil.hasText(targetDirectoryPath)) {
            File targetFile = new File(targetDirectoryPath);
            if (targetFile.exists()) {
                if (targetFile.isFile()) {
                    fileChooser.setSelectedFile(targetFile);
                } else if (targetFile.isDirectory()) {
                    String newExportSelectionName = Messages.getString("LocalExport.newExportSelectionName"); //$NON-NLS-1$
                    fileChooser.setSelectedFile(new File(newExportSelectionName));
                }
            }
        }

        File selectedFile;

        if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION
            || (selectedFile = fileChooser.getSelectedFile()) == null) {
            outputFolder = null;
            return;
        } else {
            if (isSaveFileMode) {
                outputFolder = ".zip".equals(FileUtil.getExtension(selectedFile.getName())) ? selectedFile //$NON-NLS-1$
                    : new File(selectedFile + ".zip"); //$NON-NLS-1$
            } else {
                outputFolder = selectedFile.isDirectory() ? selectedFile : selectedFile.getParentFile();
            }
            Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(LAST_DIR,
                outputFolder.isDirectory() ? outputFolder.getPath() : outputFolder.getParent());
        }
    }

    @Override
    public void closeAdditionalWindow() {
        // Do nothing
    }

    @Override
    public void resetoDefaultValues() {
        // Do nothing
    }

    @Override
    public void exportDICOM(final CheckTreeModel model, JProgressBar info) throws IOException {
        final String format = (String) comboBoxImgFormat.getSelectedItem();
        browseImgFile(format);
        if (outputFolder != null) {
            final File exportDir = outputFolder.getCanonicalFile();

            final ExplorerTask task = new ExplorerTask(Messages.getString("LocalExport.exporting"), false) { //$NON-NLS-1$

                @Override
                protected Boolean doInBackground() throws Exception {
                    dicomModel.firePropertyChange(
                        new ObservableEvent(ObservableEvent.BasicAction.LOADING_START, dicomModel, null, this));
                    if (EXPORT_FORMAT[0].equals(format)) {
                        writeDicom(this, exportDir, model, false);
                    } else if (EXPORT_FORMAT[1].equals(format)) {
                        writeDicom(this, exportDir, model, true);
                    } else {
                        writeOther(this, exportDir, model, format);
                    }
                    return true;
                }

                @Override
                protected void done() {
                    dicomModel.firePropertyChange(
                        new ObservableEvent(ObservableEvent.BasicAction.LOADING_STOP, dicomModel, null, this));
                }

            };
            task.execute();
        }
    }

    private static String getinstanceFileName(MediaElement img) {
        Integer instance = TagD.getTagValue(img, Tag.InstanceNumber, Integer.class);
        if (instance != null) {
            String val = instance.toString();
            if (val.length() < 5) {
                char[] chars = new char[5 - val.length()];
                for (int i = 0; i < chars.length; i++) {
                    chars[i] = '0';
                }

                return new String(chars) + val;

            } else {
                return val;
            }
        }
        return TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
    }

    private void writeOther(ExplorerTask task, File exportDir, CheckTreeModel model, String format) {
        Properties pref = Activator.IMPORT_EXPORT_PERSISTENCE;
        boolean keepNames = Boolean.valueOf(pref.getProperty(KEEP_INFO_DIR, Boolean.TRUE.toString()));
        int jpegQuality = StringUtil.getInteger(pref.getProperty(IMG_QUALITY, null), 80);
        boolean more8bits = Boolean.valueOf(pref.getProperty(HEIGHT_BITS, Boolean.FALSE.toString()));
        boolean writeGraphics = chckbxGraphics.isSelected();

        try {
            synchronized (model) {
                TreePath[] paths = model.getCheckingPaths();
                for (TreePath treePath : paths) {
                    if (task.isCancelled()) {
                        return;
                    }

                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();

                    if (node.getUserObject() instanceof DicomImageElement) {
                        DicomImageElement img = (DicomImageElement) node.getUserObject();
                        // Get instance number instead SOPInstanceUID to handle multiframe
                        String instance = getinstanceFileName(img);
                        if (!keepNames) {
                            instance = makeFileIDs(instance);
                        }
                        String path = buildPath(img, keepNames, node);
                        File destinationDir = new File(exportDir, path);
                        destinationDir.mkdirs();

                        RenderedImage image = img.getImage(null);

                        if (EXPORT_FORMAT[2].equals(format)) {
                            if (image != null) {
                                image = img.getRenderedImage(image);
                            }
                            if (image != null) {
                                File destinationFile = new File(destinationDir, instance + ".jpg"); //$NON-NLS-1$
                                ImageFiler.writeJPG(destinationFile, image, jpegQuality / 100.0f);
                                if (writeGraphics) {
                                    XmlSerializer.writePresentation(img, destinationFile);
                                }
                            } else {
                                LOGGER.error("Cannot export DICOM file to {}: {}", format, //$NON-NLS-1$
                                    img.getFileCache().getOriginalFile());
                            }
                        }
                        if (EXPORT_FORMAT[3].equals(format)) {
                            if (image != null) {
                                image = img.getRenderedImage(image);
                            }
                            if (image != null) {
                                File destinationFile = new File(destinationDir, instance + ".png"); //$NON-NLS-1$
                                ImageFiler.writePNG(destinationFile, image);
                                if (writeGraphics) {
                                    XmlSerializer.writePresentation(img, destinationFile);
                                }
                            } else {
                                LOGGER.error("Cannot export DICOM file to {}: {}", format, //$NON-NLS-1$
                                    img.getFileCache().getOriginalFile());
                            }
                        }
                        if (EXPORT_FORMAT[4].equals(format)) {
                            if (image != null) {
                                if (!more8bits) {
                                    image = img.getRenderedImage(image);
                                }
                                File destinationFile = new File(destinationDir, instance + ".tif"); //$NON-NLS-1$
                                ImageFiler.writeTIFF(destinationFile, image, false, false, false);
                                if (writeGraphics) {
                                    XmlSerializer.writePresentation(img, destinationFile);
                                }
                            } else {
                                LOGGER.error("Cannot export DICOM file to {}: {}", format, //$NON-NLS-1$
                                    img.getFileCache().getOriginalFile());
                            }
                        }

                        // Prevent to many files open on Linux (Ubuntu => 1024) and close image stream
                        img.removeImageFromCache();
                    } else if (node.getUserObject() instanceof MediaElement
                        && node.getUserObject() instanceof FileExtractor) {
                        MediaElement dcm = (MediaElement) node.getUserObject();
                        File fileSrc = ((FileExtractor) dcm).getExtractFile();
                        if (fileSrc != null) {
                            // Get instance number instead SOPInstanceUID to handle multiframe
                            String instance = getinstanceFileName(dcm);
                            if (!keepNames) {
                                instance = makeFileIDs(instance);
                            }
                            String path = buildPath(dcm, keepNames, node);
                            File destinationDir = new File(exportDir, path);
                            destinationDir.mkdirs();

                            File destinationFile =
                                new File(destinationDir, instance + FileUtil.getExtension(fileSrc.getName()));
                            FileUtil.nioCopyFile(fileSrc, destinationFile);
                        }
                    }
                }

            }
        } catch (Exception e) {
            LOGGER.error("Cannot extract media from DICOM", e);
        }
    }

    private void writeDicom(ExplorerTask task, File exportDir, CheckTreeModel model, boolean zipFile)
        throws IOException {
        boolean keepNames;
        boolean writeDicomdir;
        boolean cdCompatible;
        boolean writeGraphics = chckbxGraphics.isSelected();

        File writeDir;

        if (zipFile) {
            keepNames = false;
            writeDicomdir = true;
            cdCompatible = true;
            writeDir = FileUtil.createTempDir(AppProperties.buildAccessibleTempDirectory("tmp", "zip")); //$NON-NLS-1$ //$NON-NLS-2$
        } else {
            Properties pref = Activator.IMPORT_EXPORT_PERSISTENCE;
            writeDicomdir = Boolean.valueOf(pref.getProperty(INC_DICOMDIR, "true"));//$NON-NLS-1$
            keepNames = writeDicomdir ? false : Boolean.valueOf(pref.getProperty(KEEP_INFO_DIR, "true"));//$NON-NLS-1$
            cdCompatible = Boolean.valueOf(pref.getProperty(CD_COMPATIBLE, "false"));//$NON-NLS-1$
            writeDir = exportDir;
        }

        DicomDirWriter writer = null;
        try {

            if (writeDicomdir) {
                File dcmdirFile = new File(writeDir, "DICOMDIR"); //$NON-NLS-1$
                writer = DicomDirLoader.open(dcmdirFile);
            }

            synchronized (model) {
                ArrayList<String> uids = new ArrayList<>();
                TreePath[] paths = model.getCheckingPaths();
                for (TreePath treePath : paths) {
                    if (task.isCancelled()) {
                        return;
                    }

                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();

                    if (node.getUserObject() instanceof DicomImageElement) {
                        DicomImageElement img = (DicomImageElement) node.getUserObject();
                        String path = buildPath(img, keepNames, writeDicomdir, cdCompatible, node);
                        File destinationDir = new File(writeDir, path);
                        
                        String iuid = TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
                        int index = uids.indexOf(iuid);
                        if (index == -1) {
                            uids.add(iuid);
                        } else {
                            buildAndWritePR(img, writeGraphics, keepNames, destinationDir, writer, node);
                            // Write only once the file for multiframe
                            continue;
                        }
                        if (!keepNames) {
                            iuid = makeFileIDs(iuid);
                        }


                        destinationDir.mkdirs();
                        File destinationFile = new File(destinationDir, iuid);
                        if (img.saveToFile(destinationFile)) {
                            Attributes imgAttributes =
                                buildAndWritePR(img, writeGraphics, keepNames, destinationDir, writer, node);
                            writeInDicomDir(writer, imgAttributes, node, iuid, destinationFile);
                        } else {
                            LOGGER.error("Cannot export DICOM file: {}", img.getFileCache().getOriginalFile()); //$NON-NLS-1$
                        }
                    } else if (node.getUserObject() instanceof DicomSpecialElement) {
                        DicomSpecialElement dcm = (DicomSpecialElement) node.getUserObject();
                        String iuid = TagD.getTagValue(dcm, Tag.SOPInstanceUID, String.class);
                        String path = buildPath(dcm, keepNames, writeDicomdir, cdCompatible, node);
                        File destinationDir = new File(writeDir, path);
                        destinationDir.mkdirs();

                        File destinationFile = new File(destinationDir, iuid);
                        if (dcm.saveToFile(destinationFile)) {
                            writeInDicomDir(writer, dcm, node, iuid, destinationFile);
                        }
                    } else if (node.getUserObject() instanceof MediaElement) {
                        MediaElement dcm = (MediaElement) node.getUserObject();
                        String iuid = TagD.getTagValue(dcm, Tag.SOPInstanceUID, String.class);
                        if (!keepNames) {
                            iuid = makeFileIDs(iuid);
                        }

                        String path = buildPath(dcm, keepNames, writeDicomdir, cdCompatible, node);
                        File destinationDir = new File(writeDir, path);
                        destinationDir.mkdirs();

                        File destinationFile = new File(destinationDir, iuid);
                        if (dcm.saveToFile(destinationFile)) {
                            writeInDicomDir(writer, dcm, node, iuid, destinationFile);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Cannot export DICOM", e);
        } finally {
            if (writer != null) {
                // Commit DICOMDIR changes and close the file
                writer.close();
            }
        }

        if (zipFile) {
            try {
                FileUtil.zip(writeDir, exportDir);
            } catch (Exception e) {
                LOGGER.error("Cannot export DICOM ZIP file: {}", exportDir, e); //$NON-NLS-1$
            } finally {
                FileUtil.recursiveDelete(writeDir);
            }
        }
    }

    private static Attributes buildAndWritePR(MediaElement img, boolean writeGraphics, boolean keepNames,
        File destinationDir, DicomDirWriter writer, DefaultMutableTreeNode node) {
        Attributes imgAttributes = img.getMediaReader() instanceof DcmMediaReader
            ? ((DcmMediaReader) img.getMediaReader()).getDicomObject() : null;
        if (writeGraphics && imgAttributes != null) {
            GraphicModel grModel = (GraphicModel) img.getTagValue(TagW.PresentationModel);
            if (grModel != null && grModel.hasSerializableGraphics()) {
                String prUid = UIDUtils.createUID();
                File outputFile = new File(destinationDir, keepNames ? prUid : makeFileIDs(prUid));
                destinationDir.mkdirs();
                Attributes prAttributes = PrSerializer.writePresentation(grModel, imgAttributes, outputFile, prUid);
                if (prAttributes != null) {
                    try {
                        writeInDicomDir(writer, imgAttributes, node, outputFile.getName(), outputFile);
                    } catch (IOException e) {
                        LOGGER.error("Writing DICOMDIR", e);
                    }
                }
            }
        }
        return imgAttributes;
    }

    public static String buildPath(MediaElement img, boolean keepNames, boolean writeDicomdir, boolean cdCompatible,
        DefaultMutableTreeNode node) {
        StringBuilder buffer = new StringBuilder();
        // Cannot keep folders names with DICOMDIR (could be not valid)
        if (keepNames && !writeDicomdir) {
            TreeNode[] objects = node.getPath();
            if (objects.length > 2) {
                for (int i = 1; i < objects.length - 1; i++) {
                    buffer.append(buildFolderName(objects[i].toString(), 30));
                    buffer.append(File.separator);
                }
            }
        } else {
            if (cdCompatible) {
                buffer.append("DICOM"); //$NON-NLS-1$
                buffer.append(File.separator);
            }
            buffer.append(makeFileIDs((String) img.getTagValue(TagW.PatientPseudoUID)));
            buffer.append(File.separator);
            buffer.append(makeFileIDs(TagD.getTagValue(img, Tag.StudyInstanceUID, String.class)));
            buffer.append(File.separator);
            buffer.append(makeFileIDs(TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class)));
        }
        return buffer.toString();
    }

    public static String buildPath(MediaElement img, boolean keepNames, DefaultMutableTreeNode node) {
        StringBuilder buffer = new StringBuilder();
        if (keepNames) {
            TreeNode[] objects = node.getPath();
            if (objects.length > 3) {
                buffer.append(buildFolderName(objects[1].toString(), 30));
                buffer.append(File.separator);
                buffer.append(buildFolderName(objects[2].toString(), 30));
                buffer.append(File.separator);
                buffer.append(buildFolderName(objects[3].toString(), 25));
                buffer.append('-');
                // Hash of UID to guaranty the unique behavior of the name.
                buffer.append(makeFileIDs(TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class)));
            }
        } else {
            buffer.append(makeFileIDs((String) img.getTagValue(TagW.PatientPseudoUID)));
            buffer.append(File.separator);
            buffer.append(makeFileIDs(TagD.getTagValue(img, Tag.StudyInstanceUID, String.class)));
            buffer.append(File.separator);
            buffer.append(makeFileIDs(TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class)));
        }
        return buffer.toString();
    }

    private static String buildFolderName(String str, int length) {
        String value = FileUtil.getValidFileNameWithoutHTML(str);
        return StringUtil.getTruncatedString(value, length, Suffix.NO);
    }

    private static boolean writeInDicomDir(DicomDirWriter writer, MediaElement img, DefaultMutableTreeNode node,
        String iuid, File destinationFile) throws IOException {
        if (writer != null) {
            if (!(img.getMediaReader() instanceof DcmMediaReader)
                || ((DcmMediaReader) img.getMediaReader()).getDicomObject() == null) {
                LOGGER.error("Cannot export DICOM file: ", img.getFileCache().getOriginalFile()); //$NON-NLS-1$
                return false;
            }
            return writeInDicomDir(writer, ((DcmMediaReader) img.getMediaReader()).getDicomObject(), node, iuid,
                destinationFile);
        }
        return false;
    }

    private static boolean writeInDicomDir(DicomDirWriter writer, Attributes dataset, DefaultMutableTreeNode node,
        String iuid, File destinationFile) throws IOException {
        if (writer != null && dataset != null) {
            Attributes fmi = dataset.createFileMetaInformation(UID.ImplicitVRLittleEndian);

            String miuid = fmi.getString(Tag.MediaStorageSOPInstanceUID, null);

            String pid = dataset.getString(Tag.PatientID, null);
            String styuid = dataset.getString(Tag.StudyInstanceUID, null);
            String seruid = dataset.getString(Tag.SeriesInstanceUID, null);

            if (styuid != null && seruid != null) {
                if (pid == null) {
                    pid = styuid;
                    dataset.setString(Tag.PatientID, VR.LO, pid);
                }
                Attributes patRec = writer.findPatientRecord(pid);
                if (patRec == null) {
                    patRec = DicomDirLoader.RecordFactory.createRecord(RecordType.PATIENT, null, dataset, null, null);
                    writer.addRootDirectoryRecord(patRec);
                }
                Attributes studyRec = writer.findStudyRecord(patRec, styuid);
                if (studyRec == null) {
                    studyRec = DicomDirLoader.RecordFactory.createRecord(RecordType.STUDY, null, dataset, null, null);
                    writer.addLowerDirectoryRecord(patRec, studyRec);
                }
                Attributes seriesRec = writer.findSeriesRecord(studyRec, seruid);
                if (seriesRec == null) {
                    seriesRec = DicomDirLoader.RecordFactory.createRecord(RecordType.SERIES, null, dataset, null, null);
                    /*
                     * Icon Image Sequence (0088,0200).This Icon Image is representative of the Series. It may or may
                     * not correspond to one of the images of the Series.
                     */
                    if (seriesRec != null && node.getParent() instanceof DefaultMutableTreeNode) {
                        Object userObject = ((DefaultMutableTreeNode) node.getParent()).getUserObject();
                        if (userObject instanceof DicomSeries) {
                            DicomImageElement midImage =
                                ((DicomSeries) userObject).getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, null, null);
                            Attributes iconItem = mkIconItem(midImage);
                            if (iconItem != null) {
                                seriesRec.newSequence(Tag.IconImageSequence, 1).add(iconItem);
                            }
                        }
                    }
                    writer.addLowerDirectoryRecord(studyRec, seriesRec);
                }
                Attributes instRec;
                if (writer.findLowerInstanceRecord(seriesRec, false, iuid) == null) {
                    instRec =
                        DicomDirLoader.RecordFactory.createRecord(dataset, fmi, writer.toFileIDs(destinationFile));
                    writer.addLowerDirectoryRecord(seriesRec, instRec);
                }
            } else {
                if (writer.findRootInstanceRecord(false, miuid) == null) {
                    Attributes instRec =
                        DicomDirLoader.RecordFactory.createRecord(dataset, fmi, writer.toFileIDs(destinationFile));
                    writer.addRootDirectoryRecord(instRec);
                }
            }
        }
        return true;
    }

    public static String makeFileIDs(String uid) {
        return StringUtil.integer2String(uid.hashCode());
    }

    public static Attributes mkIconItem(DicomImageElement image) {
        if (image == null) {
            return null;
        }
        BufferedImage thumbnail = null;
        PlanarImage imgPl = image.getImage(null);
        if (imgPl != null) {
            RenderedImage img = image.getRenderedImage(imgPl);
            final double scale = Math.min(128 / (double) img.getHeight(), 128 / (double) img.getWidth());
            final PlanarImage thumb = scale < 1.0
                ? SubsampleAverageDescriptor.create(img, scale, scale, Thumbnail.DownScaleQualityHints).getRendering()
                : PlanarImage.wrapRenderedImage(img);
            thumbnail = thumb.getAsBufferedImage();
        }
        // Prevent to many files open on Linux (Ubuntu => 1024) and close image stream
        image.removeImageFromCache();

        if (thumbnail == null) {
            return null;
        }
        int w = thumbnail.getWidth();
        int h = thumbnail.getHeight();

        String pmi = TagD.getTagValue(image, Tag.PhotometricInterpretation, String.class);
        BufferedImage bi = thumbnail;
        if (thumbnail.getColorModel().getColorSpace().getType() != ColorSpace.TYPE_GRAY) {
            bi = convertBI(thumbnail, BufferedImage.TYPE_BYTE_INDEXED);
            pmi = "PALETTE COLOR"; //$NON-NLS-1$
        }

        byte[] iconPixelData = new byte[w * h];
        Attributes iconItem = new Attributes();

        if ("PALETTE COLOR".equals(pmi)) { //$NON-NLS-1$
            IndexColorModel cm = (IndexColorModel) bi.getColorModel();
            int[] lutDesc = { cm.getMapSize(), 0, 8 };
            byte[] r = new byte[lutDesc[0]];
            byte[] g = new byte[lutDesc[0]];
            byte[] b = new byte[lutDesc[0]];
            cm.getReds(r);
            cm.getGreens(g);
            cm.getBlues(b);
            iconItem.setInt(Tag.RedPaletteColorLookupTableDescriptor, VR.US, lutDesc);
            iconItem.setInt(Tag.GreenPaletteColorLookupTableDescriptor, VR.US, lutDesc);
            iconItem.setInt(Tag.BluePaletteColorLookupTableDescriptor, VR.US, lutDesc);
            iconItem.setBytes(Tag.RedPaletteColorLookupTableData, VR.OW, r);
            iconItem.setBytes(Tag.GreenPaletteColorLookupTableData, VR.OW, g);
            iconItem.setBytes(Tag.BluePaletteColorLookupTableData, VR.OW, b);

            Raster raster = bi.getRaster();
            for (int y = 0, i = 0; y < h; ++y) {
                for (int x = 0; x < w; ++x, ++i) {
                    iconPixelData[i] = (byte) raster.getSample(x, y, 0);
                }
            }
        } else {
            pmi = "MONOCHROME2"; //$NON-NLS-1$
            for (int y = 0, i = 0; y < h; ++y) {
                for (int x = 0; x < w; ++x, ++i) {
                    iconPixelData[i] = (byte) bi.getRGB(x, y);
                }
            }
        }
        iconItem.setString(Tag.PhotometricInterpretation, VR.CS, pmi);
        iconItem.setInt(Tag.Rows, VR.US, h);
        iconItem.setInt(Tag.Columns, VR.US, w);
        iconItem.setInt(Tag.SamplesPerPixel, VR.US, 1);
        iconItem.setInt(Tag.BitsAllocated, VR.US, 8);
        iconItem.setInt(Tag.BitsStored, VR.US, 8);
        iconItem.setInt(Tag.HighBit, VR.US, 7);
        iconItem.setBytes(Tag.PixelData, VR.OW, iconPixelData);
        return iconItem;
    }

    private static BufferedImage convertBI(BufferedImage src, int imageType) {
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), imageType);
        Graphics2D big = dst.createGraphics();
        try {
            big.drawImage(src, 0, 0, null);
        } finally {
            big.dispose();
        }
        return dst;
    }

}
