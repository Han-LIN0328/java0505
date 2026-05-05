import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class Main {

    public static void main(String[] args) {
        try {
            // 讀取目標影像
            File inputFile = new File("twodc.png");
            BufferedImage image = ImageIO.read(inputFile);
            if (image == null) {
                throw new IOException("無法讀取影像檔案，請檢查檔案是否存在或格式是否支援: " + inputFile);
            }
            
            int width = image.getWidth();
            int height = image.getHeight();
            int[] histogram = new int[256];
            int[][] grayPixels = new int[width][height];
            
            // 1. 轉換為灰階並統計直方圖
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    int rgb = image.getRGB(i, j);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                    grayPixels[i][j] = gray;
                    histogram[gray]++;
                }
            }
            
            // 2. 計算最佳閾值 T_opt (Otsu's method)
            int totalPixels = width * height;
            float sum = 0;
            for (int i = 0; i < 256; i++) sum += i * histogram[i];
            
            float sumB = 0;
            int wB = 0;
            int wF = 0;
            float varMax = 0;
            int threshold = 0;
            
            for (int i = 0; i < 256; i++) {
                wB += histogram[i];
                if (wB == 0) continue;
                wF = totalPixels - wB;
                if (wF == 0) break;
                
                sumB += (float) (i * histogram[i]);
                float mB = sumB / wB;
                float mF = (sum - sumB) / wF;
                
                float varBetween = (float) wB * (float) wF * (mB - mF) * (mB - mF);
                
                if (varBetween > varMax) {
                    varMax = varBetween;
                    threshold = i;
                }
            }
            
            // 3. 二值化與連通區域標記 (CCL)
            int[][] binary = new int[width][height];
            int[][] labels = new int[width][height];
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    binary[i][j] = (grayPixels[i][j] > threshold) ? 1 : 0;
                }
            }
            
            int labelCount = 0;
            int[] dx = {1, -1, 0, 0, 1, 1, -1, -1};
            int[] dy = {0, 0, 1, -1, 1, -1, 1, -1};
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    if (binary[i][j] == 1 && labels[i][j] == 0) {
                        labelCount++;
                        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
                        queue.add(new int[] {i, j});
                        labels[i][j] = labelCount;
                        while (!queue.isEmpty()) {
                            int[] p = queue.removeFirst();
                            int x = p[0];
                            int y = p[1];
                            for (int k = 0; k < dx.length; k++) {
                                int nx = x + dx[k];
                                int ny = y + dy[k];
                                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                                    if (binary[nx][ny] == 1 && labels[nx][ny] == 0) {
                                        labels[nx][ny] = labelCount;
                                        queue.add(new int[] {nx, ny});
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. 計算每個標籤面積並找出最大的兩個
            int[] area = new int[labelCount + 1];
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int lab = labels[x][y];
                    if (lab > 0) area[lab]++;
                }
            }

            int largest = 0, second = 0;
            for (int k = 1; k <= labelCount; k++) {
                if (area[k] > area[largest]) {
                    second = largest;
                    largest = k;
                } else if (area[k] > area[second]) {
                    second = k;
                }
            }

            // 5. 建立選取遮罩並填滿內部空洞 (確保色塊是實心的)
            boolean[][] mask = new boolean[width][height];
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    mask[x][y] = (labels[x][y] == largest) || (labels[x][y] == second);
                }
            }

            // 簡單膨脹讓邊緣平滑
            int dilateIterations = 3;
            int[] mx = {1, -1, 0, 0, 1, 1, -1, -1, 0};
            int[] my = {0, 0, 1, -1, 1, -1, 1, -1, 0};
            for (int it = 0; it < dilateIterations; it++) {
                boolean[][] next = new boolean[width][height];
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        if (mask[x][y]) { next[x][y] = true; continue; }
                        boolean hit = false;
                        for (int d = 0; d < mx.length; d++) {
                            int nx = x + mx[d];
                            int ny = y + my[d];
                            if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                                if (mask[nx][ny]) { hit = true; break; }
                            }
                        }
                        next[x][y] = hit;
                    }
                }
                mask = next;
            }

            // 6. 繪製最終結果影像 (純色塊風格，無文字)
            BufferedImage outputImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            
            // 設定顏色
            int bgColor = 0xFFFFFFFF;    // 純白背景
            int dogColor = 0xFF60B6F6;   // 淺藍色 (Dog)
            int catColor = 0xFFBBE0FD;   // 淡藍色 (Cat)

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (!mask[x][y]) {
                        // 背景一律純白
                        outputImage.setRGB(x, y, bgColor);
                    } else {
                        int lab = labels[x][y];
                        if (lab == largest) {
                            outputImage.setRGB(x, y, dogColor);
                        } else {
                            outputImage.setRGB(x, y, catColor);
                        }
                    }
                }
            }

            // 7. 儲存檔案
            File outputFile = new File("solid_mask_result_notext.png");
            ImageIO.write(outputImage, "png", outputFile);
            System.out.println("影像處理完畢！已輸出無文字純色塊的分割圖：solid_mask_result_notext.png");
            
        } catch (IOException e) {
            System.err.println("讀取或寫入影像時發生錯誤: " + e.getMessage());
        }
    }
}