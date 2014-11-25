/*
 * Copyright 2009-2014 DigitalGlobe, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.mrgeo.resources.mrspyramid;

import org.mrgeo.services.mrspyramid.MrsPyramidService;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;


@Path("/metadata")
public class MetadataResource
{
    @Context
    MrsPyramidService service;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{output: .*+}")
    public Response getMetadata(@PathParam("output") final String imgName)
    {
        try
        {
            return Response.status(Status.OK).entity( service.getMetadata(imgName) ).build();
        }
        catch (final IOException e)
        {
            final String error = e.getMessage() != null ? e.getMessage() : "";
            return Response.serverError().entity(error).build();
        }
    }
}