package com.lecture.course.watermark;

import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * 가시적 워터마크 — 미리보기 이미지에 반투명 텍스트를 대각선으로 반복 배치한다.
 *
 * 목적은 '무단 사용 방지'이지 '완전한 차단'이 아니다. 구매 전 사용자는 구도와 색감은
 * 확인할 수 있어야 하므로, 이미지를 알아볼 수 없을 만큼 가리지는 않는다.
 */
@Component
public class VisibleWatermarker {

    private static final float OPACITY = 0.30f;
    private static final double ANGLE = Math.toRadians(-30);

    public BufferedImage apply(BufferedImage source, String text) {
        int width = source.getWidth();
        int height = source.getHeight();

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.drawImage(source, 0, 0, null);

        int fontSize = Math.max(14, width / 20);
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int stepX = textWidth + fontSize * 2;
        int stepY = fontSize * 4;

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, OPACITY));
        AffineTransform origin = g.getTransform();
        g.rotate(ANGLE, width / 2.0, height / 2.0);

        // 회전 후에도 모서리가 비지 않도록 캔버스보다 넉넉한 범위를 채운다
        int margin = Math.max(width, height);
        for (int y = -margin; y < height + margin; y += stepY) {
            for (int x = -margin; x < width + margin; x += stepX) {
                g.setColor(new Color(0, 0, 0, 90));
                g.drawString(text, x + 2, y + 2);
                g.setColor(Color.WHITE);
                g.drawString(text, x, y);
            }
        }
        g.setTransform(origin);

        // 하단 고정 라벨
        int barHeight = Math.max(28, height / 16);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        g.setColor(Color.BLACK);
        g.fillRect(0, height - barHeight, width, barHeight);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g.setColor(Color.WHITE);
        int labelSize = Math.max(11, barHeight / 2);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, labelSize));
        g.drawString("PREVIEW - " + text, 12, height - barHeight / 2 + labelSize / 2 - 2);

        g.dispose();
        return out;
    }
}
