package com.j256.ormlite.db;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.SQLException;
import java.util.List;

import javax.sql.rowset.serial.SerialBlob;

import com.j256.ormlite.field.DataPersister;
import com.j256.ormlite.field.FieldConverter;
import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.misc.SqlExceptionUtil;
import com.j256.ormlite.support.DatabaseResults;

/**
 * Derby database type information used to create the tables, etc.. This is for an embedded Derby database. For client
 * connections to a remote Derby server, you should use {@link DerbyClientServerDatabaseType}.
 * 
 * @author graywatson
 */
public class DerbyEmbeddedDatabaseType extends BaseDatabaseType implements DatabaseType {

	protected final static String DATABASE_URL_PORTION = "derby";
	private final static String DRIVER_CLASS_NAME = "org.apache.derby.jdbc.EmbeddedDriver";
	private final static String DATABASE_NAME = "Derby";

	private static FieldConverter objectConverter;
	private static FieldConverter booleanConverter;
	private static FieldConverter charConverter;

	public boolean isDatabaseUrlThisType(String url, String dbTypePart) {
		if (!DATABASE_URL_PORTION.equals(dbTypePart)) {
			return false;
		}
		// jdbc:derby:sample;
		String[] parts = url.split(":");
		return (parts.length >= 3 && !parts[2].startsWith("//"));
	}

	@Override
	protected String getDriverClassName() {
		return DRIVER_CLASS_NAME;
	}

	@Override
	public String getDatabaseName() {
		return DATABASE_NAME;
	}

	@Override
	public FieldConverter getFieldConverter(DataPersister dataType) {
		// we are only overriding certain types
		switch (dataType.getSqlType()) {
			case BOOLEAN :
				if (booleanConverter == null) {
					booleanConverter = new BooleanNumberFieldConverter();
				}
				return booleanConverter;
			case CHAR :
				if (charConverter == null) {
					charConverter = new CharFieldConverter();
				}
				return charConverter;
			case SERIALIZABLE :
				if (objectConverter == null) {
					objectConverter = new ObjectFieldConverter();
				}
				return objectConverter;
			default :
				return super.getFieldConverter(dataType);
		}
	}
	@Override
	protected void appendLongStringType(StringBuilder sb, int fieldWidth) {
		sb.append("LONG VARCHAR");
	}

	@Override
	public void appendOffsetValue(StringBuilder sb, int offset) {
		// I love the required ROWS prefix. Hilarious.
		sb.append("OFFSET ").append(offset).append(" ROWS ");
	}

	@Override
	protected void appendBooleanType(StringBuilder sb, int fieldWidth) {
		// I tried "char for bit data" and "char(1)" with no luck
		sb.append("SMALLINT");
	}

	@Override
	protected void appendCharType(StringBuilder sb, int fieldWidth) {
		sb.append("SMALLINT");
	}

	@Override
	protected void appendByteType(StringBuilder sb, int fieldWidth) {
		sb.append("SMALLINT");
	}

	@Override
	protected void appendByteArrayType(StringBuilder sb, int fieldWidth) {
		sb.append("LONG VARCHAR FOR BIT DATA");
	}

	@Override
	protected void configureGeneratedId(StringBuilder sb, FieldType fieldType, List<String> statementsBefore,
			List<String> additionalArgs, List<String> queriesAfter) {
		sb.append("GENERATED BY DEFAULT AS IDENTITY ");
		configureId(sb, fieldType, statementsBefore, additionalArgs, queriesAfter);
	}

	@Override
	public void appendEscapedEntityName(StringBuilder sb, String word) {
		sb.append('\"').append(word).append('\"');
	}

	@Override
	public boolean isLimitSqlSupported() {
		return false;
	}

	@Override
	public String getPingStatement() {
		return "SELECT 1 FROM SYSIBM.SYSDUMMY1";
	}

	@Override
	public boolean isEntityNamesMustBeUpCase() {
		return true;
	}

	/**
	 * Conversion from the Object Java field to the BLOB Jdbc type because the varbinary needs a size otherwise.
	 */
	private static class ObjectFieldConverter implements FieldConverter {
		public SqlType getSqlType() {
			return SqlType.BLOB;
		}
		public Object javaToSqlArg(FieldType fieldType, Object javaObject) throws SQLException {
			ByteArrayOutputStream outStream = new ByteArrayOutputStream();
			try {
				ObjectOutputStream objOutStream = new ObjectOutputStream(outStream);
				objOutStream.writeObject(javaObject);
			} catch (Exception e) {
				throw SqlExceptionUtil.create("Could not write serialized object to output stream", e);
			}
			return new SerialBlob(outStream.toByteArray());
		}
		public Object parseDefaultString(FieldType fieldType, String defaultStr) throws SQLException {
			throw new SQLException("Default values for serializable types are not supported");
		}
		public Object resultToJava(FieldType fieldType, DatabaseResults results, int columnPos) throws SQLException {
			InputStream stream = results.getBlobStream(columnPos);
			if (stream == null) {
				return null;
			}
			try {
				ObjectInputStream objInStream = new ObjectInputStream(stream);
				return objInStream.readObject();
			} catch (Exception e) {
				throw SqlExceptionUtil.create("Could not read serialized object from result blob", e);
			}
		}
		public boolean isStreamType() {
			return true;
		}
	}

	/**
	 * Conversion from the char Java field because Derby can't convert Character to type char. Jesus.
	 */
	private static class CharFieldConverter implements FieldConverter {
		public SqlType getSqlType() {
			return SqlType.INTEGER;
		}
		public Object javaToSqlArg(FieldType fieldType, Object javaObject) throws SQLException {
			char character = (char) (Character) javaObject;
			return (int) character;
		}
		public Object parseDefaultString(FieldType fieldType, String defaultStr) throws SQLException {
			if (defaultStr.length() != 1) {
				throw new SQLException("Problems with field " + fieldType + ", default string to long: '" + defaultStr
						+ "'");
			}
			return (int) defaultStr.charAt(0);
		}
		public Object resultToJava(FieldType fieldType, DatabaseResults results, int columnPos) throws SQLException {
			int intVal = results.getInt(columnPos);
			return (char) intVal;
		}
		public boolean isStreamType() {
			return false;
		}
	}
}
