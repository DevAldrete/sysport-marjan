package mx.marjan.requests;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.math.BigDecimal;
import javax.swing.JDialog;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import mx.marjan.finance.AdvanceService;
import mx.marjan.finance.ExpenseService;
import mx.marjan.finance.Invoice;
import mx.marjan.finance.InvoiceService;
import mx.marjan.shared.Async;
import mx.marjan.shared.Dates;
import mx.marjan.shared.Money;
import mx.marjan.shared.Ui;
import mx.marjan.trips.Delivery;
import mx.marjan.trips.DeliveryService;
import mx.marjan.trips.Trip;
import mx.marjan.trips.TripService;

/** FR-REQ-5: read-only "whole story" of a request: trip, costs, delivery and invoice. */
final class ServiceRequestDetailDialog {

    private final ServiceRequest request;
    private final TripService trips = new TripService();
    private final ExpenseService expenses = new ExpenseService();
    private final AdvanceService advances = new AdvanceService();
    private final DeliveryService deliveries = new DeliveryService();
    private final InvoiceService invoices = new InvoiceService();

    private ServiceRequestDetailDialog(ServiceRequest request) {
        this.request = request;
    }

    static void show(Component parent, ServiceRequest request) {
        ServiceRequestDetailDialog dialog = new ServiceRequestDetailDialog(request);
        Async.run(dialog::build, text -> dialog.render(parent, text), failure -> Ui.failure(parent, failure));
    }

    private String build() {
        StringBuilder text = new StringBuilder()
                .append("Folio: ").append(request.folio()).append('\n')
                .append("Cliente: ").append(request.clientName()).append('\n')
                .append("Ruta: ").append(request.routeLabel()).append('\n')
                .append("Estado: ").append(request.status().label()).append('\n')
                .append("Tarifa: ").append(request.agreedRate() == null ? "-" : Money.format(request.agreedRate()))
                .append("\n\n");
        trips.findByRequest(request.id()).ifPresent(trip -> appendTrip(text, trip));
        return text.toString();
    }

    private void appendTrip(StringBuilder text, Trip trip) {
        text.append("--- Viaje ---\n")
                .append("Unidad: ").append(trip.vehicleLabel()).append('\n')
                .append("Operador: ").append(trip.employeeName()).append('\n')
                .append("Estado: ").append(trip.status().label()).append('\n')
                .append("Salida: ").append(Dates.format(trip.departure())).append('\n')
                .append("Llegada: ").append(Dates.format(trip.arrival())).append('\n')
                .append("Km reales: ").append(trip.actualKm() == null ? "-" : trip.actualKm()).append('\n');
        BigDecimal spent = expenses.listByTrip(trip.id()).stream()
                .map(expense -> expense.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        text.append("Gastos: ").append(Money.format(spent)).append('\n')
                .append("Anticipo: ").append(advances.balanceForTrip(trip.id()).label()).append('\n');
        deliveries.findByTrip(trip.id()).ifPresent(delivery -> appendDelivery(text, delivery));
        invoices.findByRequest(trip.serviceRequestId()).ifPresent(invoice -> appendInvoice(text, invoice));
    }

    private void appendDelivery(StringBuilder text, Delivery delivery) {
        text.append("--- Entrega ---\n")
                .append("Fecha: ").append(Dates.format(delivery.actualDatetime())).append('\n')
                .append("Recibio: ").append(delivery.receivedBy()).append('\n')
                .append("Evidencia: ").append(delivery.evidenceReference()).append('\n')
                .append("Estado: ").append(delivery.status().label()).append('\n');
    }

    private void appendInvoice(StringBuilder text, Invoice invoice) {
        text.append("--- Factura ---\n")
                .append("No.: ").append(invoice.invoiceNumber()).append('\n')
                .append("Importe: ").append(Money.format(invoice.amount())).append('\n')
                .append("Pagado: ").append(Money.format(invoice.paid())).append('\n')
                .append("Saldo: ").append(Money.format(invoice.balance())).append('\n')
                .append("Estado: ").append(invoice.status().label()).append('\n');
    }

    private void render(Component parent, String text) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent),
                "Detalle " + request.folio(), Dialog.ModalityType.APPLICATION_MODAL);
        JTextArea area = new JTextArea(text, 18, 60);
        area.setEditable(false);
        dialog.add(Ui.scroll(area), BorderLayout.CENTER);
        dialog.add(Ui.row(Ui.button("Cerrar", dialog::dispose)), BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}
