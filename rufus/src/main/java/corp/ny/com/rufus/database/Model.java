package corp.ny.com.rufus.database;

import android.content.ContentValues;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import corp.ny.com.rufus.database.annotation.Column;
import corp.ny.com.rufus.database.annotation.Table;
import corp.ny.com.rufus.database.exceptions.TableException;
import corp.ny.com.rufus.system.RufusApp;


/**
 * Created by yann-yvan on 05/12/17.
 * Current DATABASE  VERSION = 1
 */

public abstract class Model<T> implements Cloneable, Serializable {

    //getTable() identify
    private String idName = "id";
    //handled model
    private T model = (T) this;
    private int lastPage = 0;
    private int lastSearchPage = 0;
    private String searchable;
    //in case of need of cursor value
    //private Cursor cloneCursor;

    public static String id() {
        return "";
    }


    /**
     * help to find field value by his name in a model
     *
     * @param object    the class model
     * @param fieldName the field name of the desired value
     */
    private static void fillAttribute(Object object, String fieldName, Cursor cursor) throws ClassNotFoundException {
        Class c = Class.forName(object.getClass().getName());
        for (Field field : c.getDeclaredFields()) {
            //skip if it is not the target field
            if (!field.getName().equals(fieldName)) continue;
            //make field accessible for reading
            if (field.getModifiers() == Modifier.PRIVATE) {
                field.setAccessible(true);
            }

            try {
                int index = cursor.getColumnIndex(fieldName);
                if (cursor.getType(index) == Cursor.FIELD_TYPE_STRING)
                    field.set(object, cursor.getString(index));
                else if (cursor.getType(index) == Cursor.FIELD_TYPE_FLOAT)
                    field.setFloat(object, cursor.getFloat(index));
                else if (cursor.getType(index) == Cursor.FIELD_TYPE_INTEGER)
                    field.setInt(object, cursor.getInt(index));
                else {
                    CharArrayBuffer buffer = new CharArrayBuffer(1024 * 4);
                    cursor.copyStringToBuffer(index, buffer);
                    //field.set(object, buffer.toString());
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Get database instance
     *
     * @return {@linkplain SQLiteDatabase} instance
     */
    protected SQLiteDatabase getDb() {
        return RufusApp.getDataBaseInstance();
    }

    /**
     * Define table name
     *
     * @return table name
     */
    public String getTableName() {
        return model.getClass().getSimpleName();
    }

    /**
     * Define by which column result should be order
     *
     * @return column name
     */
    public String getOrderBy() {
        return idName;
    }

    /**
     * Get the value of the used model
     *
     * @return id value
     */
    public String getIdValue(){
        for (Field field : getClass().getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(corp.ny.com.rufus.database.annotation.Column.class)) {
                if (field.getAnnotation(Column.class).primary()) {
                    try {
                        return (String) field.get(this);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Define table identifier column
     *
     * @return identify column name
     * DefResponse identify column name is <b>id</b>
     */
    public String getIdName() {
        return idName;
    }

    public String getSearchable() {
        return searchable;
    }

    public void setSearchable(String searchable) {
        this.searchable = searchable;
    }

    /**
     * Define table limit result per query by range
     *
     * @return the number of row to return per query
     * DefResponse value is <b>5</b>
     */
    public int getLimit() {
        //limit
        return 5;
    }

    public int getLastPage() {
        return lastPage;
    }

    /**
     * Insert values into a table
     *
     * @return the model inserted into the table on <b>null</b> if something went wrong
     */
    public T save() {
        try {
            long success = getDb().insertWithOnConflict(getTableName(), null, sqlQueryBuilder(new ContentValues()), SQLiteDatabase.CONFLICT_FAIL);
            if (success > 0) {
                // Log.e("Save ", String.valueOf(success));
                return find(success);
            }
        } catch (SQLiteConstraintException e) {
            //e.printStackTrace();
            return update();
        }
        return null;
    }


    /**
     * Method for delete
     *
     * @return boolean true if success
     */
    public boolean delete() {
        int success = getDb().delete(getTableName(), getIdName() + "=?", new String[]{getIdValue()});
        return success > 0;
    }

    /**
     * Count the total of row in the table
     *
     * @return total of row
     */
    public int count() {
        int total = 0;
        Cursor cursor = getDb().rawQuery(String.format("SELECT * FROM %s", getTableName()), null);
        if (cursor != null) {
            //cloneCursor = cursor;
            total = cursor.getCount();
            cursor.close();
        }
        return total;
    }


    /**
     * Update a model in the table
     *
     * @return the model up to date from the table on <b>null</b> if something went wrong
     */
    public T update() {
        try {
            int success = getDb().update(getTableName(), sqlQueryBuilder(new ContentValues()), getIdName() + "=?", new String[]{getIdValue()});
            if (success > 0) {
                //Log.e("new Id ", String.valueOf(success));
                return find(success);
            }
        } catch (SQLiteConstraintException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Method to find model by <i>id</i>
     *
     * @param id represent the unique value that identify a row or model
     * @return the model found or <b>null</b> if nothing found in table
     */
    public T find(String id) {
        Cursor cursor = getDb().query(getTableName(), null, getIdName() + " LIKE ?",
                new String[]{id}, null, null, null);
        if (cursor != null) {
            //cloneCursor = cursor;
            if (cursor.moveToNext()) {
                return cursorToModel(cursor);
            }
            cursor.close();
        }
        return null;
    }

    /**
     * Method to find model by <i>id</i>
     *
     * @param id represent the unique value that identify a row or model
     * @return the model found or <b>null</b> if nothing found in table
     */
    public T find(int id) {
        Cursor cursor = getDb().query(getTableName(), null, getIdName() + " LIKE ?",
                new String[]{String.valueOf(id)}, null, null, null);
        if (cursor != null) {
            //cloneCursor = cursor;
            if (cursor.moveToNext()) {
                return cursorToModel(cursor);
            }
            cursor.close();
        }
        return null;
    }

    /**
     * Method to find model by <i>id</i>
     *
     * @param id represent the unique value that identify a row or model
     * @return the model found or <b>null</b> if nothing found in table
     */
    public T find(long id) {
        Cursor cursor = getDb().query(getTableName(), null, getIdName() + " LIKE ?",
                new String[]{String.valueOf(id)}, null, null, null);
        if (cursor != null) {
            //cloneCursor = cursor;
            if (cursor.moveToNext()) {
                return cursorToModel(cursor);
            }
            cursor.close();
        }
        return null;
    }

    public T refresh() {
        return find(getIdValue());
    }

    /**
     * Method to find all model from a table
     *
     * @return a list of model found or an<b>empty list</b> if nothing found in table
     * @deprecated
     */
    public ArrayList<T> findAll() {
        ArrayList<T> result = new ArrayList<>();
        Cursor cursor = getDb().rawQuery(String.format("SELECT * FROM %s ORDER BY %s", getTableName(), getOrderBy()), null);
        if (cursor != null) {
            //cloneCursor = cursor;
            while (cursor.moveToNext()) {
                result.add(cursorToModel(cursor));
            }
            cursor.close();
        }
        return result;
    }

    /**
     * Method to find all model from a table
     *
     * @return a list of model found or an<b>empty list</b> if nothing found in table
     */
    public ArrayList<T> all() {
        return findAll();
    }

    public ArrayList<T> search(String query) {
        ArrayList<T> result = new ArrayList<>();
        Cursor cursor = getDb().query(getTableName(), null, getSearchable() + " LIKE ?",
                new String[]{"%" + query + "%"}, null, null, getOrderBy());
        //Cursor cursor = getDb().rawQuery(String.format("SELECT * FROM %s WHERE ? LIKE ? ORDER BY ? LIMIT ?,?", getTableName()),
        //new String[]{getSearchable(), "%" + query + "%", getOrderBy(), String.valueOf(lastSearchPage), String.valueOf(getLimit())});
        if (cursor != null) {
            //cloneCursor = cursor;
            //Log.e("size", cursor.getCount() + "");
            while (cursor.moveToNext()) {
                result.add(cursorToModel(cursor));
            }
            cursor.close();
        }
        return result;
    }

    /**
     * Method for find information by lastID and get result list
     *
     * @param lastID the id of the last model
     * @return a list of model found starting from the value nested the last id or an<b>empty list</b> if nothing found in table
     * <br>the list is paginate <b>default value is 5 per result</b>
     */
    public ArrayList<T> findByRange(String lastID) {
        ArrayList<T> result = new ArrayList<>();
        System.out.print(lastID);
        Cursor cursor = getDb().rawQuery(String.format("SELECT * FROM %s WHERE %s > ? ORDER BY %s LIMIT %s", getTableName(), getIdName(), getOrderBy(), getLimit()),
                new String[]{lastID});
        if (cursor != null) {
            //cloneCursor = cursor;
            while (cursor.moveToNext()) {
                result.add(cursorToModel(cursor));
            }
            cursor.close();
        }
        return result;
    }

    /**
     * Method for find information by lastID and get result list
     *
     * @return a list of model found starting from the value nested the last id or an<b>empty list</b> if nothing found in table
     * <br>the list is paginate <b>default value is 5 per result</b>
     */
    public ArrayList<T> paginate() {
        ArrayList<T> result = new ArrayList<>();

        Cursor cursor = getDb().rawQuery(String.format("SELECT * FROM %s ORDER BY %s LIMIT %s,%s", getTableName(), getOrderBy(), lastPage, getLimit()), null);
        if (cursor != null) {
            //cloneCursor = cursor;
            while (cursor.moveToNext()) {
                result.add(cursorToModel(cursor));
            }
            cursor.close();
        }

        lastPage += result.size();

        return result;
    }

    /**
     * Method for find information by lastID and get result list
     *
     * @param lastID the id of the last model
     * @return a list of model found starting from the value nested the last id or an<b>empty list</b> if nothing found in table
     * <br>the list is paginate <b>default value is 5 per result</b>
     */
    public ArrayList<T> findByRange(int lastID) {
        ArrayList<T> result = new ArrayList<>();
        System.out.print(lastID);
        Cursor cursor = getDb().rawQuery(String.format("SELECT * FROM %s WHERE %s > ? ORDER BY %s LIMIT %s", getTableName(), getIdName(), getOrderBy(), getLimit()),
                new String[]{String.valueOf(lastID)});
        if (cursor != null) {
            // cloneCursor = cursor;
            while (cursor.moveToNext()) {
                result.add(cursorToModel(cursor));
            }
            cursor.close();
        }
        return result;
    }

    /**
     * Method for find information by lastID and get result list
     *
     * @param lastID the id of the last model
     * @return a list of model found starting from the value nested the last id or an<b>empty list</b> if nothing found in table
     * <br>the list is paginate <b>default value is 5 per result</b>
     */
    public ArrayList<T> findByRangeInvert(int lastID) {
        ArrayList<T> result = new ArrayList<>();
        System.out.print(lastID);
        Cursor cursor = getDb().rawQuery(String.format("SELECT * FROM %s WHERE %s < ? ORDER BY %s LIMIT %s", getTableName(), getIdName(), getOrderBy(), getLimit()),
                new String[]{String.valueOf(lastID)});
        if (cursor != null) {
            //cloneCursor = cursor;
            while (cursor.moveToNext()) {
                result.add(cursorToModel(cursor));
            }
            cursor.close();
        }
        return result;
    }

    public String toFCUpperCase(String e) {
        String finalString = "";
        e = e.trim();
        for (String str : e.split(" ")) {
            finalString = finalString.trim();
            //finalString.concat(finalString.substring(0, 1).toUpperCase()
        }
        return e;
    }

    /**
     * Convert a model to sql query
     * <br> <b>example</b><br>
     * <quote>
     * ContentValues query = new ContentValues();<br>
     * if (obj.getId() != 0) {
     * query.put("id", obj.getId());
     * }<br>
     * query.put("types_id", obj.getTypeID());<br>
     * query.put("name", obj.getName());<br>
     * return query;<br>
     * </code>
     *
     * @param query will contain the value of the of the model in query string
     * @return and object that will be use for update and insert query
     */
    public ContentValues sqlQueryBuilder(ContentValues query) {
        return prepareStatement(model, query);
    }

    /**
     * Convert a row result to a model
     * <p> <b>example</b><br>
     * <blockquote>
     * <pre>
     * return new Model(cursor.getInt(0), cursor.getInt(1), cursor.getString(2));
     * </pre></blockquote>
     *
     * @param cursor object containing all query rows
     * @return the model with all attribute like save in the table
     */
    private T cursorToModel(Cursor cursor) {
        T object = null;
        try {
            object = (T) clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        for (String column :
                cursor.getColumnNames()) {
            try {
                assert object != null;
                fillAttribute(object, column, cursor);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        return object;
    }

    /**
     * @return
     */
    public String buildTable() {
        Schema schema = Schema.instantiate(getTableName());
        tableStructure(schema);
        return schema.toString();
    }


    /**
     * @return SQlIte query to build table from annotation
     */
    public String genTable() throws TableException {
        return getSchema().toString();
    }

    public Schema getSchema() throws TableException {
        Schema schema = Schema.instantiate(getTableName(), this);
        tableStructure(schema);
        return  schema;
    }


    /**
     * <blockquote>
     * <b>Sample</b>
     * <pre>
     * table.increments("id");
     * table.string("name");
     * table.string("phone", 15).nullable().defValue("6 XX XX XX XX");
     * table.string("email").unique();
     * table.string("deviceToken").nullable();
     * table.string("profilePicture").nullable();
     * table.string("password").nullable();
     * </pre></blockquote>
     *
     * @param table
     */
    public void tableStructure(@NonNull Schema table) {}

    @Override
    public String toString() {
        return printClass(model);
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    /**
     * @param object
     */
    public String printClass(T object) {
        try {
            Class c = Class.forName(object.getClass().getName());
            String result = "{";

            for (Field field : c.getDeclaredFields()) {
                field.setAccessible(true);
                try {

                    result = result.concat(String.format("\n\t\"%s\" : \"%s\",", field.getName(), field.get(object)));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            return result.concat("}");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Get Json representation of the object
     *
     * @return Json string
     */
    public String toJson() {
        List<String> json = new ArrayList<>();
        try {
            Class c = Class.forName(this.getClass().getName());
            for (Field field : c.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    json.add(String.format("\n\t\"%s\" : \"%s\"", field.getName(), field.get(this)));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            return String.format("{%s}", TextUtils.join(",", json.toArray()));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return "{}";
    }

    /**
     * @param object the model to extract data
     * @param values the object where extracted data from model will be put
     * @return an object with model data extracted
     */
    public ContentValues prepareStatement(final T object, ContentValues values) {
        try {
            Class c = Class.forName(object.getClass().getName());


            for (Field field : c.getDeclaredFields()) {
                if (!field.isAccessible()) field.setAccessible(true);
                try {
                    if (field.getName().equals("serialVersionUID")) continue;
                    //For annotated classes
                    if(c.isAnnotationPresent(Table.class) && field.isAnnotationPresent(corp.ny.com.rufus.database.annotation.Column.class)){
                        populate(object,field,values);
                    }else{
                        //For non annotate classes
                        populate(object,field,values);
                    }

                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return values;
    }

    /**
     * Put field value into sql query parameter
     * @param object
     * @param field
     * @param values
     * @throws IllegalAccessException
     */
    private void populate(final T object,Field field,ContentValues values) throws IllegalAccessException {
        if (field.getType() == String.class)
            values.put(field.getName(), (String) field.get(object));
        else if (field.getType() == int.class)
            values.put(field.getName(), (Integer) field.get(object));
        else if (field.getType() == boolean.class)
            values.put(field.getName(), (Boolean) field.get(object));
        else if (field.getType() == double.class)
            values.put(field.getName(), (Double) field.get(object));
        else if (field.getType() == float.class)
            values.put(field.getName(), (Float) field.get(object));
        else if (field.getType() == short.class)
            values.put(field.getName(), (Short) field.get(object));
        else if (field.getType() == long.class)
            values.put(field.getName(), (Long) field.get(object));
        else if (field.getType() == byte.class)
            values.put(field.getName(), (Byte) field.get(object));
    }
}
