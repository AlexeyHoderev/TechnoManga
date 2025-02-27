package com.example.ad;

public class Page {
    private long id;
    private long comicId;
    private int pageNumber;

    // Getters and setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getComicId() { return comicId; }
    public void setComicId(long comicId) { this.comicId = comicId; }
    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }
}