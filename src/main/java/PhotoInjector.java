import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.awt.RenderingHints;
import javax.imageio.ImageIO;
import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionInfo;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@ExtensionInfo(
        Title = "PhotoInjector",
        Description = "Inject photos into retros",
        Version = "1.5",
        Author = "DanielaNaomi"
)

public class PhotoInjector extends ExtensionForm {
    public int idfurni;
    public String startcoords = "";
    public String initialCoords = "";
    public CheckBox setcoords_cbx;
    private static final int PART_SIZE = 320;
    public TextField width;
    public TextField height;
    public ImageView imagepath;
    public ProgressBar bar;
    public CheckBox activate_cbx;
    public CheckBox always_on_top_cbx;
    public RadioButton leftdirection;
    public ToggleGroup directions;
    public RadioButton rightdirection;
    public Button buttoninject;
    public RadioButton posterfurni;
    public ToggleGroup furnitype;
    public RadioButton photofurni;
    public TextField delay;
    public RadioButton normalmode;
    public ToggleGroup injectsmodes;
    public RadioButton bypassmode;
    public TextField reducequality;
    public CheckBox fixedaspectratio;
    private boolean isrunning = false;
    private int counter = 0;
    private BufferedImage currentImage;
    public String state = "General";
    private double imageAspectRatio = 1.0;

    @Override
    protected void initExtension() {
        intercept(HMessage.Direction.TOCLIENT, "CameraStorageUrl", this::InCameraStorageUrl);
        intercept(HMessage.Direction.TOCLIENT, "UnseenItems", this::InUnseenItems);
        intercept(HMessage.Direction.TOCLIENT, "CameraPurchaseOK", this::InCameraPurchaseOK);
        intercept(HMessage.Direction.TOSERVER, "PlaceObject", this::OutPlaceObject);
        intercept(HMessage.Direction.TOSERVER, "MoveWallItem", this::OutMoveWallItem);

        setupClipboardShortcut();
        setupTextFieldValidation();
        setupAspectRatioHandling();
    }

    private void setupAspectRatioHandling() {
        fixedaspectratio.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue && currentImage != null) {
                updateAspectRatio();
            }
        });
    }

    private void updateAspectRatio() {
        if (currentImage != null) {
            imageAspectRatio = (double) currentImage.getWidth() / currentImage.getHeight();
        }
    }

    public void toggleAlwaysOnTop() {
        primaryStage.setAlwaysOnTop(always_on_top_cbx.isSelected());
    }

    private void OutMoveWallItem(HMessage hMessage) {
        if (setcoords_cbx.isSelected()) {
            hMessage.setBlocked(true);
            hMessage.getPacket().readInteger();
            startcoords = hMessage.getPacket().readString();
            initialCoords = startcoords;
            setcoords_cbx.setSelected(false);
        }
    }

    private void setupTextFieldValidation() {
        width.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                width.setText(newValue.replaceAll("\\D", ""));
            } else if (!newValue.isEmpty() && fixedaspectratio.isSelected() && currentImage != null) {
                int newWidth = Integer.parseInt(newValue);
                int newHeight = (int) Math.round(newWidth / imageAspectRatio);
                if (height.getText().isEmpty() || !height.getText().equals(String.valueOf(newHeight))) {
                    height.setText(String.valueOf(newHeight));
                }
            }
        });

        height.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                height.setText(newValue.replaceAll("\\D", ""));
            } else if (!newValue.isEmpty() && fixedaspectratio.isSelected() && currentImage != null) {
                int newHeight = Integer.parseInt(newValue);
                int newWidth = (int) Math.round(newHeight * imageAspectRatio);
                if (width.getText().isEmpty() || !width.getText().equals(String.valueOf(newWidth))) {
                    width.setText(String.valueOf(newWidth));
                }
            }
        });

        delay.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                delay.setText(newValue.replaceAll("\\D", ""));
            }
        });

        reducequality.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                reducequality.setText(newValue.replaceAll("\\D", ""));
            }
            int value = newValue.isEmpty() ? 0 : Integer.parseInt(newValue);
            if (value > 100) {
                reducequality.setText("100");
            }
        });

        width.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue && width.getText().isEmpty()) {
                width.setText("0");
            }
        });

        height.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue && height.getText().isEmpty()) {
                height.setText("0");
            }
        });

        delay.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue && delay.getText().isEmpty()) {
                delay.setText("0");
            }
        });

        reducequality.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue && reducequality.getText().isEmpty()) {
                reducequality.setText("100");
            }
        });
    }

    private void setupClipboardShortcut() {
        KeyCombination ctrlV = new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN);
        primaryStage.getScene().setOnKeyPressed(event -> {
            if (ctrlV.match(event)) {
                handleClipboardImage();
            }
        });
    }

    private BufferedImage reduceImageQuality(BufferedImage originalImage) {
        double qualityPercent = Double.parseDouble(reducequality.getText()) / 100.0;
        int newWidth = (int)(originalImage.getWidth() * qualityPercent);
        int newHeight = (int)(originalImage.getHeight() * qualityPercent);

        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resizedImage.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        return resizedImage;
    }

    private void handleClipboardImage() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                java.awt.Image awtImage = (java.awt.Image) clipboard.getData(DataFlavor.imageFlavor);
                BufferedImage bufferedImage = convertToBufferedImage(awtImage);
                currentImage = reduceImageQuality(bufferedImage);

                Platform.runLater(() -> {
                    Image fxImage = convertToFXImage(currentImage);
                    imagepath.setImage(fxImage);

                    updateAspectRatio();
                    if (fixedaspectratio.isSelected()) {
                        width.setText(String.valueOf(currentImage.getWidth() / PART_SIZE + 1));
                        height.setText(String.valueOf(currentImage.getHeight() / PART_SIZE + 1));
                    }
                });
            }
        } catch (UnsupportedFlavorException | IOException e) {
            e.printStackTrace();
        }
    }

    private BufferedImage convertToBufferedImage(java.awt.Image img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }

        BufferedImage bufferedImage = new BufferedImage(
                img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.drawImage(img, 0, 0, null);
        g2d.dispose();
        return bufferedImage;
    }

    private Image convertToFXImage(BufferedImage bufferedImage) {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("temp", ".png");
            tempFile.deleteOnExit();
            ImageIO.write(bufferedImage, "png", tempFile);
            Image image = new Image(tempFile.toURI().toString());
            if (!tempFile.delete()) {
                tempFile.deleteOnExit();
            }
            return image;
        } catch (IOException e) {
            e.printStackTrace();
            if (tempFile != null && tempFile.exists()) {
                if (!tempFile.delete()) {
                    tempFile.deleteOnExit();
                }
            }
            return null;
        }
    }

    public void importphoto() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Image File");

        FileChooser.ExtensionFilter imageFilter = new FileChooser.ExtensionFilter(
                "Image Files",
                "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.tiff", "*.webp"
        );
        fileChooser.getExtensionFilters().add(imageFilter);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files", "*.*"));

        File selectedFile = fileChooser.showOpenDialog(primaryStage);
        if (selectedFile != null) {
            try {
                BufferedImage originalImage = ImageIO.read(selectedFile);
                if (originalImage != null) {
                    currentImage = reduceImageQuality(originalImage);
                    Image fxImage = convertToFXImage(currentImage);
                    imagepath.setImage(fxImage);

                    updateAspectRatio();
                    if (fixedaspectratio.isSelected()) {
                        width.setText(String.valueOf(currentImage.getWidth() / PART_SIZE + 1));
                        height.setText(String.valueOf(currentImage.getHeight() / PART_SIZE + 1));
                    }
                } else {
                    System.out.println("Unable to read image file: " + selectedFile.getName());
                }
            } catch (IOException e) {
                System.out.println("Error loading image: " + e.getMessage());
            }
        }
    }

    public void injectphoto() {
        if (!activate_cbx.isSelected()) {
            return;
        }

        if (currentImage == null || startcoords.equals("") || initialCoords.equals("")) {
            return;
        }

        if (!isrunning) {
            isrunning = true;
            buttoninject.setText("Stop");

            int gridWidth = Integer.parseInt(width.getText());
            int gridHeight = Integer.parseInt(height.getText());
            BufferedImage resizedImage = resizeImage(currentImage, PART_SIZE * gridWidth, PART_SIZE * gridHeight);
            List<BufferedImage> parts = splitImage(resizedImage, gridWidth, gridHeight);

            Thread processThread = new Thread(() -> {
                try {
                    int totalParts = parts.size();
                    for (int i = 0; i < totalParts; i++) {
                        if (!isrunning) {
                            break;
                        }

                        BufferedImage part = parts.get(i);
                        File tempFile = null;
                        try {
                            tempFile = File.createTempFile("part", ".png");
                            tempFile.deleteOnExit();
                            ImageIO.write(part, "png", tempFile);
                            byte[] partBytes = Files.readAllBytes(tempFile.toPath());
                            HPacket renderPacket = createPacket(partBytes);
                            sendToServer(renderPacket);

                            final double progress = (double) (i + 1) / totalParts;
                            Platform.runLater(() -> bar.setProgress(progress));

                            Thread.sleep(Integer.parseInt(delay.getText()));
                        } finally {
                            if (tempFile != null && tempFile.exists()) {
                                if (!tempFile.delete()) {
                                    tempFile.deleteOnExit();
                                }
                            }
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }

                Platform.runLater(() -> {
                    startcoords = "";
                    initialCoords = "";
                    counter = 0;
                    bar.setProgress(0);
                    activate_cbx.setSelected(false);
                    buttoninject.setText("Inject");
                    isrunning = false;
                });
            });

            processThread.start();
        } else {
            Platform.runLater(() -> {
                buttoninject.setText("Inject");
                startcoords = "";
                initialCoords = "";
                counter = 0;
                bar.setProgress(0);
                activate_cbx.setSelected(false);
                isrunning = false;
            });
        }
    }

    private void InCameraPurchaseOK(HMessage hMessage) {
        if (activate_cbx.isSelected()) {
            updateCoordinates();
            counter++;

            if (counter >= Integer.parseInt(width.getText())) {
                resetToInitialCoords();
                counter = 0;
            }
        }
    }

    private void OutPlaceObject(HMessage hMessage) {
        if (setcoords_cbx.isSelected()) {
            hMessage.setBlocked(true);

            int initialIndex = hMessage.getPacket().getReadIndex();

            try {
                hMessage.getPacket().readInteger();
                hMessage.getPacket().readString();
                startcoords = hMessage.getPacket().readString();
                initialCoords = startcoords;
                state = "Special";
            } catch (Exception e) {
                hMessage.getPacket().setReadIndex(initialIndex);

                startcoords = hMessage.getPacket().readString();
                int index = startcoords.indexOf(':');
                if (index != -1) {
                    startcoords = startcoords.substring(index);
                }
                initialCoords = startcoords;
                state = "General";
            }

            setcoords_cbx.setSelected(false);
        }
    }

    private void InUnseenItems(HMessage hMessage) {
        if (activate_cbx.isSelected()) {
            hMessage.getPacket().readInteger();
            hMessage.getPacket().readInteger();
            hMessage.getPacket().readInteger();
            idfurni = hMessage.getPacket().readInteger();
            if (state.equals("General")) {
                sendToServer(new HPacket("PlaceObject", HMessage.Direction.TOSERVER, idfurni + " " + startcoords + " "));
            } else if (state.equals("Special")) {
                sendToServer(new HPacket("PlaceObject", HMessage.Direction.TOSERVER, idfurni, "i", startcoords));
            }
        }
    }

    private void InCameraStorageUrl(HMessage hMessage) {
        if (activate_cbx.isSelected()) {
            sendToServer(new HPacket("PurchasePhoto", HMessage.Direction.TOSERVER, ""));
        }
    }

    private void updateCoordinates() {
        String[] parts = startcoords.split(" ");
        String[] wParts = parts[0].split("=")[1].split(",");
        String[] lParts = parts[1].split("=")[1].split(",");
        String direction = parts[2];

        int w1 = Integer.parseInt(wParts[0]);
        int w2 = Integer.parseInt(wParts[1]);
        int l1 = Integer.parseInt(lParts[0]);
        int l2 = Integer.parseInt(lParts[1]);

        if (direction.equals("l")) {
            if (posterfurni.isSelected()) {
                if (leftdirection.isSelected()) {
                    w2 -= 1;
                    l1 += 4;
                    l2 -= 2;
                } else if (rightdirection.isSelected()) {
                    w2 += 1;
                    l1 -= 4;
                    l2 += 2;
                }
            } else if (photofurni.isSelected()) {
                if (leftdirection.isSelected()) {
                    l1 += 7;
                    l2 -= 4;
                } else if (rightdirection.isSelected()) {
                    l1 -= 7;
                    l2 += 4;
                }
            }
            startcoords = String.format(":w=%d,%d l=%d,%d l", w1, w2, l1, l2);
        } else if (direction.equals("r")) {
            if (posterfurni.isSelected()) {
                if (leftdirection.isSelected()) {
                    w1 += 2;
                    l1 -= 12;
                    l2 -= 6;
                } else if (rightdirection.isSelected()) {
                    w1 -= 2;
                    l1 += 12;
                    l2 += 6;
                }
            } else if (photofurni.isSelected()) {
                if (leftdirection.isSelected()) {
                    l1 += 7;
                    l2 += 4;
                } else if (rightdirection.isSelected()) {
                    l1 -= 7;
                    l2 -= 4;
                }
            }
            startcoords = String.format(":w=%d,%d l=%d,%d r", w1, w2, l1, l2);
        }
    }

    private void resetToInitialCoords() {
        Thread delayThread = new Thread(() -> {
            try {
                Thread.sleep(1000);

                String[] parts = initialCoords.split(" ");
                String[] wParts = parts[0].split("=")[1].split(",");
                String[] lParts = parts[1].split("=")[1].split(",");
                String direction = parts[2];

                final int w1 = Integer.parseInt(wParts[0]);
                final int w2 = Integer.parseInt(wParts[1]);
                final int l1 = Integer.parseInt(lParts[0]);
                int l2Final = Integer.parseInt(lParts[1]);

                if (posterfurni.isSelected()) {
                    l2Final -= 23;
                } else if (photofurni.isSelected()) {
                    l2Final -= 8;
                }

                final int l2 = l2Final;

                Platform.runLater(() -> {
                    if (direction.equals("l")) {
                        startcoords = String.format(":w=%d,%d l=%d,%d l", w1, w2, l1, l2);
                        initialCoords = startcoords;
                    }
                    if (direction.equals("r")) {
                        startcoords = String.format(":w=%d,%d l=%d,%d r", w1, w2, l1, l2);
                        initialCoords = startcoords;
                    }
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        delayThread.start();
    }

    private BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        return resizedImage;
    }

    private List<BufferedImage> splitImage(BufferedImage image, int gridWidth, int gridHeight) {
        List<BufferedImage> parts = new ArrayList<>();

        if (leftdirection.isSelected()) {
            for (int row = gridHeight - 1; row >= 0; row--) {
                for (int col = 0; col < gridWidth; col++) {
                    int x = col * PART_SIZE;
                    int y = row * PART_SIZE;

                    BufferedImage part = new BufferedImage(PART_SIZE, PART_SIZE, image.getType());
                    Graphics2D g2d = part.createGraphics();
                    g2d.drawImage(image.getSubimage(x, y, PART_SIZE, PART_SIZE), 0, 0, null);
                    g2d.dispose();
                    parts.add(part);
                }
            }
        } else if (rightdirection.isSelected()) {
            for (int row = gridHeight - 1; row >= 0; row--) {
                for (int col = gridWidth - 1; col >= 0; col--) {
                    int x = col * PART_SIZE;
                    int y = row * PART_SIZE;

                    BufferedImage part = new BufferedImage(PART_SIZE, PART_SIZE, image.getType());
                    Graphics2D g2d = part.createGraphics();
                    g2d.drawImage(image.getSubimage(x, y, PART_SIZE, PART_SIZE), 0, 0, null);
                    g2d.dispose();
                    parts.add(part);
                }
            }
        }

        return parts;
    }

    private HPacket createPacket(byte[] data) {
        if (normalmode.isSelected()) {
            HPacket packet = new HPacket("RenderRoom", HMessage.Direction.TOSERVER);
            packet.appendInt(data.length);
            packet.appendBytes(data);
            return packet;
        } else if (bypassmode.isSelected()) {
            String base64Image = Base64.getEncoder().encodeToString(data);
            String rot13Image = applyRot13(base64Image);
            return new HPacket("RenderRoom", HMessage.Direction.TOSERVER, "", rot13Image);
        }
        return null;
    }

    private String applyRot13(String input) {
        StringBuilder result = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (c >= 'a' && c <= 'z') {
                result.append((char) ('a' + (c - 'a' + 13) % 26));
            } else if (c >= 'A' && c <= 'Z') {
                result.append((char) ('A' + (c - 'A' + 13) % 26));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}