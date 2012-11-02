/*
 * Copyright (C) 2010-2012 Serge Rieder
 * eugene.fradkin@gmail.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.jkiss.dbeaver.tools.data.impl;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.data.DBDAttributeBinding;
import org.jkiss.dbeaver.model.data.DBDContent;
import org.jkiss.dbeaver.model.data.DBDContentStorage;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.tools.data.IDataExporterSite;
import org.jkiss.dbeaver.utils.ContentUtils;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.List;

/**
 * CSV Exporter
 */
public class DataExporterXML extends DataExporterAbstract {

    private PrintWriter out;
    private List<DBDAttributeBinding> columns;
    private String tableName;

    @Override
    public void init(IDataExporterSite site) throws DBException
    {
        super.init(site);
        out = site.getWriter();
    }

    @Override
    public void dispose()
    {
        out = null;
        super.dispose();
    }

    @Override
    public void exportHeader(DBRProgressMonitor monitor) throws DBException, IOException
    {
        columns = getSite().getAttributes();
        printHeader();
    }

    private void printHeader()
    {
        out.write("<?xml version=\"1.0\" ?>\n");
        tableName = escapeXmlElementName(getSite().getSource().getName());
        out.write("<!DOCTYPE " + tableName + " [\n");
        out.write("  <!ELEMENT " + tableName + " (DATA_RECORD*)>\n");
        out.write("  <!ELEMENT DATA_RECORD (");
        int columnsSize = columns.size();
        for (int i = 0; i < columnsSize; i++) {
            out.write(escapeXmlElementName(columns.get(i).getAttribute().getName()) + "?");
            if (i < columnsSize - 1) {
                out.write(",");
            }
        }
        out.write(")+>\n");
        for (int i = 0; i < columnsSize; i++) {
            out.write("  <!ELEMENT " + escapeXmlElementName(columns.get(i).getAttribute().getName()) + " (#PCDATA)>\n");
        }
        out.write("]>\n");
        out.write("<" + tableName + ">\n");
    }

    @Override
    public void exportRow(DBRProgressMonitor monitor, Object[] row) throws DBException, IOException
    {
        out.write("  <DATA_RECORD>\n");
        for (int i = 0; i < row.length; i++) {
            DBDAttributeBinding column = columns.get(i);
            String columnName = escapeXmlElementName(column.getAttribute().getName());
            out.write("    <" + columnName + ">");
            if (DBUtils.isNullValue(row[i])) {
                writeTextCell(null);
            } else if (row[i] instanceof DBDContent) {
                // Content
                // Inline textual content and handle binaries in some special way
                DBDContent content = (DBDContent)row[i];
                try {
                    DBDContentStorage cs = content.getContents(monitor);
                    if (ContentUtils.isTextContent(content)) {
                        writeCellValue(cs.getContentReader());
                    } else {
                        getSite().writeBinaryData(cs.getContentStream(), cs.getContentLength());
                    }
                }
                finally {
                    content.release();
                }
            } else {
                writeTextCell(super.getValueDisplayString(column, row[i]));
            }
            out.write("</" + columnName + ">\n");
        }
        out.write("  </DATA_RECORD>\n");
    }

    @Override
    public void exportFooter(DBRProgressMonitor monitor) throws IOException
    {
        out.write("</" + tableName + ">\n");
    }

    private void writeTextCell(String value)
    {
        if (value != null) {
            value = value.replace("<", "&lt;").replace(">", "&gt;").replace("&", "&amp;");
            out.write(value);
        }
    }

    private void writeImageCell(File file) throws DBException
    {
        if (file != null && file.exists()) {
            Image image = null;
            try {
                image = ImageIO.read(file);
            } catch (IOException e) {
                throw new DBException("Can't read an exported image " + image, e);
            }

            if (image != null) {
                String imagePath = file.getAbsolutePath();
                imagePath = "files/" + imagePath.substring(imagePath.lastIndexOf(File.separator));
                out.write(imagePath);
            }
        }
    }

    private void writeCellValue(Reader reader) throws IOException
    {
        try {
            // Copy reader
            char buffer[] = new char[2000];
            for (;;) {
                int count = reader.read(buffer);
                if (count <= 0) {
                    break;
                }
                for (int i = 0; i < count; i++) {
                    if (buffer[i] == '<') {
                        out.write("&lt;");
                    }
                    else if (buffer[i] == '>') {
                        out.write("&gt;");
                    }
                    if (buffer[i] == '&') {
                        out.write("&amp;");
                    }
                    out.write(buffer[i]);
                }
            }
        } finally {
            ContentUtils.close(reader);
        }
    }

    private String escapeXmlElementName(String name) {
        return name.replaceAll("[^\\p{Alpha}\\p{Digit}]+","_");
    }
}
