package ca.yorku.realestate.ui.gui;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.ui.Formats;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/** Table model for search results; one row per listing, in ranked order. */
public class ResultsTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    static final String[] COLUMNS = {"ID", "Type", "Neighborhood", "Price", "Beds", "Baths", "Sq ft", "$ / sq ft"};

    private transient List<Listing> listings = List.of();

    public void setListings(List<Listing> newListings) {
        listings = newListings == null ? List.of() : List.copyOf(newListings);
        fireTableDataChanged();
    }

    public Listing getListingAt(int row) {
        return listings.get(row);
    }

    public List<Listing> getListings() {
        return listings;
    }

    @Override
    public int getRowCount() {
        return listings.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        Listing l = listings.get(row);
        return switch (column) {
            case 0 -> l.getId();
            case 1 -> Formats.type(l);
            case 2 -> l.getNeighborhood();
            case 3 -> Formats.money(l.getPrice());
            case 4 -> l.getBedrooms();
            case 5 -> l.getBathrooms();
            case 6 -> Formats.squareFeet(l);
            case 7 -> Formats.pricePerSqFt(l);
            default -> throw new IndexOutOfBoundsException("column " + column);
        };
    }
}
