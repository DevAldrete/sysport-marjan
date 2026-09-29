package mx.marjan.shared;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.text.DecimalFormat;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

/**
 * JFreeChart factories styled to match {@link Theme}. Kept generic (arrays of
 * labels/values) so the shared package does not depend on feature records.
 */
public final class Charts {

    private Charts() {}

    /** A bar series with an overlaid line series (e.g. revenue vs. margin). */
    public static JComponent barLine(String valueAxisLabel, String[] categories,
            double[] bars, String barName, Color barColor,
            double[] line, String lineName, Color lineColor) {
        DefaultCategoryDataset barData = new DefaultCategoryDataset();
        DefaultCategoryDataset lineData = new DefaultCategoryDataset();
        for (int i = 0; i < categories.length; i++) {
            barData.addValue(bars[i], barName, categories[i]);
            lineData.addValue(line[i], lineName, categories[i]);
        }
        JFreeChart chart = ChartFactory.createBarChart(null, null, valueAxisLabel,
                barData, PlotOrientation.VERTICAL, true, true, false);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setDataset(1, lineData);
        plot.mapDatasetToRangeAxis(1, 0);

        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, barColor);
        renderer.setShadowVisible(false);
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setDrawBarOutline(false);
        renderer.setMaximumBarWidth(0.18);

        LineAndShapeRenderer lineRenderer = new LineAndShapeRenderer(true, true);
        lineRenderer.setSeriesPaint(0, lineColor);
        lineRenderer.setSeriesStroke(0, new BasicStroke(2.4f));
        plot.setRenderer(1, lineRenderer);

        style(chart);
        return panel(chart);
    }

    /** A single bar series. */
    public static JComponent bar(String valueAxisLabel, String[] categories,
            double[] values, String seriesName, Color color) {
        DefaultCategoryDataset data = new DefaultCategoryDataset();
        for (int i = 0; i < categories.length; i++) {
            data.addValue(values[i], seriesName, categories[i]);
        }
        JFreeChart chart = ChartFactory.createBarChart(null, null, valueAxisLabel,
                data, PlotOrientation.VERTICAL, false, true, false);
        CategoryPlot plot = chart.getCategoryPlot();
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, color);
        renderer.setShadowVisible(false);
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setDrawBarOutline(false);
        renderer.setMaximumBarWidth(0.5);
        style(chart);
        return panel(chart);
    }

    /** A pie chart with a given color palette. */
    public static JComponent pie(String[] keys, double[] values, Color[] palette) {
        DefaultPieDataset<String> data = new DefaultPieDataset<>();
        for (int i = 0; i < keys.length; i++) {
            data.setValue(keys[i], values[i]);
        }
        JFreeChart chart = ChartFactory.createPieChart(null, data, true, true, false);
        PiePlot<?> plot = (PiePlot<?>) chart.getPlot();
        plot.setBackgroundPaint(Theme.CARD);
        plot.setOutlineVisible(false);
        plot.setShadowPaint(null);
        plot.setSectionOutlinesVisible(true);
        plot.setDefaultSectionOutlinePaint(Theme.CARD);
        plot.setDefaultSectionOutlineStroke(new BasicStroke(2f));
        plot.setLabelFont(Theme.regular(11f));
        plot.setLabelBackgroundPaint(null);
        plot.setLabelOutlinePaint(null);
        plot.setLabelShadowPaint(null);
        plot.setLabelLinkPaint(Theme.MUTED);
        plot.setLabelGenerator(new StandardPieSectionLabelGenerator("{0} ({2})"));
        plot.setInteriorGap(0.02);
        for (int i = 0; i < keys.length && i < palette.length; i++) {
            plot.setSectionPaint(keys[i], palette[i]);
        }
        chart.setBackgroundPaint(Theme.CARD);
        if (chart.getLegend() != null) {
            chart.getLegend().setItemFont(Theme.regular(11f));
            chart.getLegend().setBackgroundPaint(Theme.CARD);
        }
        return panel(chart);
    }

    private static void style(JFreeChart chart) {
        chart.setBackgroundPaint(Theme.CARD);
        chart.setBorderVisible(false);
        if (chart.getLegend() != null) {
            chart.getLegend().setItemFont(Theme.regular(11f));
            chart.getLegend().setBackgroundPaint(Theme.CARD);
        }

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Theme.CARD);
        plot.setOutlineVisible(false);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinePaint(new Color(0xEC, 0xEF, 0xF3));
        plot.setRangeGridlineStroke(new BasicStroke(1f));

        CategoryAxis domain = plot.getDomainAxis();
        domain.setTickLabelFont(Theme.regular(10.5f));
        domain.setTickLabelPaint(Theme.MUTED);
        domain.setAxisLineVisible(false);
        domain.setTickMarksVisible(false);
        domain.setCategoryMargin(0.12);

        NumberAxis range = (NumberAxis) plot.getRangeAxis();
        range.setTickLabelFont(Theme.regular(10.5f));
        range.setTickLabelPaint(Theme.MUTED);
        range.setAxisLineVisible(false);
        range.setTickMarksVisible(false);
        range.setNumberFormatOverride(new DecimalFormat("#,##0"));
    }

    private static JComponent panel(JFreeChart chart) {
        ChartPanel panel = new ChartPanel(chart);
        panel.setPreferredSize(new Dimension(420, 220));
        panel.setBorder(BorderFactory.createEmptyBorder());
        panel.setOpaque(false);
        panel.setMouseWheelEnabled(false);
        panel.setDomainZoomable(false);
        panel.setRangeZoomable(false);
        return panel;
    }
}
