package com.km.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.km.java.model.Constant;
import com.km.model.Book;
import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.util.JSON;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class BookService {

    @Autowired
    private MongoClient mongoClient;

    @Autowired
    private ObjectMapper objectMapper;

    private MongoCollection<Document> bookCollection;

    private MongoCollection<Document> getbookCollection() {
        if(bookCollection==null)
            this.mongoClient.getDatabase(Constant.MONGODB_DATABASE).getCollection(Constant.MONGODB_BOOK_COLLECTION);
        return this.bookCollection;
    }

    private Book documentToBook(Document document){
        try {
            Book book = objectMapper.readValue(JSON.serialize(document),Book.class);
            Document score = mongoClient.getDatabase(Constant.MONGODB_DATABASE).getCollection(Constant.MONGODB_AVERAGE_BOOKS).find(Filters.eq("bid",book.getBid())).first();
            if(score == null ||score.isEmpty())
                book.setScore(0D);
            else
                book.setScore(score.getDouble("avg"));
            return book;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Document bookToDocument(Book book){
        try {
            Document document = Document.parse(objectMapper.writeValueAsString(book));
            return document;
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Book> getBooksByBids(List<Integer> ids){
        List<Book> result = new ArrayList<>();
        FindIterable<Document> documents = getbookCollection().find(Filters.in("bid",ids));
        for(Document document:documents){
            result.add(documentToBook(document));
        }
        return result;
    }

    public Book findBookInfo(int bid){
        Document document = getbookCollection().find(new Document("bid",bid)).first();
        if(null == document ||document.isEmpty())
            return null;
        return documentToBook(document);
    }
}
