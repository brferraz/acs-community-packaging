/*
 * Copyright (C) 2005-2007 Alfresco Software Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

 * As a special exception to the terms and conditions of version 2.0 of 
 * the GPL, you may redistribute this Program in connection with Free/Libre 
 * and Open Source Software ("FLOSS") applications as described in Alfresco's 
 * FLOSS exception.  You should have recieved a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing"
 */
package org.alfresco.web.bean.users;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.transaction.UserTransaction;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.search.impl.lucene.QueryParser;
import org.alfresco.repo.security.authentication.AuthenticationException;
import org.alfresco.service.cmr.repository.InvalidNodeRefException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.search.SearchParameters;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.QName;
import org.alfresco.web.app.Application;
import org.alfresco.web.app.context.IContextListener;
import org.alfresco.web.app.context.UIContextService;
import org.alfresco.web.bean.LoginBean;
import org.alfresco.web.bean.dialog.BaseDialogBean;
import org.alfresco.web.bean.repository.MapNode;
import org.alfresco.web.bean.repository.Node;
import org.alfresco.web.bean.repository.Repository;
import org.alfresco.web.ui.common.Utils;
import org.alfresco.web.ui.common.component.UIActionLink;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author Kevin Roast
 */
public class UsersDialog extends BaseDialogBean implements IContextListener
{
   private static Log    logger = LogFactory.getLog(UsersDialog.class);

   public static final String ERROR_PASSWORD_MATCH = "error_password_match";
   private static final String ERROR_DELETE = "error_delete_user";
   private static final String ERROR_USER_DELETE = "error_delete_user_object";
   
   private static final String DEFAULT_OUTCOME = "manageUsers";
   private static final String DIALOG_CLOSE = "dialog:close";

   protected UsersBeanProperties properties;
   private List<Node> users = Collections.<Node>emptyList();
   
   
   // ------------------------------------------------------------------------------
   // Construction

   /**
    * Default Constructor
    */
   public UsersDialog()
   {
      UIContextService.getInstance(FacesContext.getCurrentInstance()).registerBean(this);
   }

   
   // ------------------------------------------------------------------------------
   // Bean property getters and setters

   /**
    * @param properties the properties to set
    */
   public void setProperties(UsersBeanProperties properties)
   {
      this.properties = properties;
   }

   /**
    * @return the list of user Nodes to display
    */
   public List<Node> getUsers()
   {
      if (this.users == null)
      {
         search();
      }
      
      return this.users;
   }
   
   /**
    * Action event called by all actions that need to setup a Person context on
    * the Users bean before an action page is called. The context will be a
    * Person Node in setPerson() which can be retrieved on the action page from
    * UsersDialog.getPerson().
    */
   public void setupUserAction(ActionEvent event)
   {
      UIActionLink link = (UIActionLink) event.getComponent();
      Map<String, String> params = link.getParameterMap();
      String id = params.get("id");
      if (id != null && id.length() != 0)
      {
         if (logger.isDebugEnabled())
            logger.debug("Setup for action, setting current Person to: " + id);

         try
         {
            // create the node ref, then our node representation
            NodeRef ref = new NodeRef(Repository.getStoreRef(), id);
            Node node = new Node(ref);
            
            // remember the Person node
            properties.setPerson(node);
            
            // clear the UI state in preparation for finishing the action
            // and returning to the main page
            contextUpdated();
         }
         catch (InvalidNodeRefException refErr)
         {
            Utils.addErrorMessage(MessageFormat.format(Application.getMessage(FacesContext
                  .getCurrentInstance(), Repository.ERROR_NODEREF), new Object[] { id }));
         }
      }
      else
      {
         properties.setPerson(null);
      }
   }

   /**
    * Action handler called when the OK button is clicked on the Delete User page
    */
   public String deleteOK()
   {
      FacesContext context = FacesContext.getCurrentInstance();
      try
      {
         String userName = (String) properties.getPerson().getProperties().get("userName");
         
         // we only delete the user auth if Alfresco is managing the authentication 
         Map session = context.getExternalContext().getSessionMap();
         if (session.get(LoginBean.LOGIN_EXTERNAL_AUTH) == null)
         {
            // delete the User authentication
            try
            {
               properties.getAuthenticationService().deleteAuthentication(userName);
            }
            catch (AuthenticationException authErr)
            {
               Utils.addErrorMessage(Application.getMessage(context, ERROR_USER_DELETE));
            }
         }
         
         // delete the associated Person
         properties.getPersonService().deletePerson(userName);
         
         // re-do the search to refresh the list
         search();
      }
      catch (Throwable e)
      {
         // rollback the transaction
         Utils.addErrorMessage(MessageFormat.format(Application.getMessage(context,
               ERROR_DELETE), e.getMessage()), e);
      }
      
      return DIALOG_CLOSE;
   }
   
   /**
    * Action handler called for the OK button press
    */
   public String changeUserDetails()
   {
      String outcome = DIALOG_CLOSE;
      
      FacesContext context = FacesContext.getCurrentInstance();
      try
      {
         Map<QName, Serializable> props = properties.getNodeService().getProperties(properties.getPerson().getNodeRef());
         props.put(ContentModel.PROP_FIRSTNAME,
               (String) properties.getPerson().getProperties().get(ContentModel.PROP_FIRSTNAME));
         props.put(ContentModel.PROP_LASTNAME,
               (String) properties.getPerson().getProperties().get(ContentModel.PROP_LASTNAME));
         props.put(ContentModel.PROP_EMAIL,
               (String) properties.getPerson().getProperties().get(ContentModel.PROP_EMAIL));
         
         // persist changes
         properties.getNodeService().setProperties(properties.getPerson().getNodeRef(), props);
         
         // if the above call was successful, then reset Person Node in the session
         Application.getCurrentUser(context).reset();
      }
      catch (Throwable err)
      {
         Utils.addErrorMessage(MessageFormat.format(Application.getMessage(
               context, Repository.ERROR_GENERIC), err.getMessage()), err );
      }
      
      return outcome;
   }

   /**
    * Event handler called when the user wishes to search for a user
    * 
    * @return The outcome
    */
   public String search()
   {
      properties.getUsersRichList().setValue(null);
      
      if (properties.getSearchCriteria() == null || properties.getSearchCriteria().trim().length() == 0)
      {
         this.users = Collections.<Node>emptyList();
      }
      else
      {
         FacesContext context = FacesContext.getCurrentInstance();
         UserTransaction tx = null;
         
         try
         {
            tx = Repository.getUserTransaction(context, true);
            tx.begin();
            
            // define the query to find people by their first or last name
            String search = properties.getSearchCriteria().trim();
            StringBuilder query = new StringBuilder(256);
            query.append("TYPE:\"{http://www.alfresco.org/model/content/1.0}person\" AND (");
            for (StringTokenizer t = new StringTokenizer(search, " "); t.hasMoreTokens(); /**/)
            {
               String term = QueryParser.escape(t.nextToken());
               query.append("((@\\{http\\://www.alfresco.org/model/content/1.0\\}firstName:*");
               query.append(term);
               query.append("*) OR (@\\{http\\://www.alfresco.org/model/content/1.0\\}lastName:*");
               query.append(term);
               query.append("*) OR (@\\{http\\://www.alfresco.org/model/content/1.0\\}userName:");
               query.append(term);
               query.append("*)) ");   // final space here is important as default OR separator
            }
            query.append(")");
            
            if (logger.isDebugEnabled())
               logger.debug("Query: " + query.toString());
   
            // define the search parameters
            SearchParameters params = new SearchParameters();
            params.setLanguage(SearchService.LANGUAGE_LUCENE);
            params.addStore(Repository.getStoreRef());
            params.setQuery(query.toString());
            
            List<NodeRef> people = properties.getSearchService().query(params).getNodeRefs();
            
            if (logger.isDebugEnabled())
               logger.debug("Found " + people.size() + " users");
            
            this.users = new ArrayList<Node>(people.size());
            
            for (NodeRef nodeRef : people)
            {
               // create our Node representation
               MapNode node = new MapNode(nodeRef);
               
               // set data binding properties
               // this will also force initialisation of the props now during the UserTransaction
               // it is much better for performance to do this now rather than during page bind
               Map<String, Object> props = node.getProperties(); 
               props.put("fullName", ((String)props.get("firstName")) + ' ' + ((String)props.get("lastName")));
               NodeRef homeFolderNodeRef = (NodeRef)props.get("homeFolder");
               if (homeFolderNodeRef != null)
               {
                  props.put("homeSpace", homeFolderNodeRef);
               }
               
               this.users.add(node);
            }
   
            // commit the transaction
            tx.commit();
         }
         catch (InvalidNodeRefException refErr)
         {
            Utils.addErrorMessage(MessageFormat.format(Application.getMessage(
                  context, Repository.ERROR_NODEREF), new Object[] {"root"}) );
            this.users = Collections.<Node>emptyList();
            try { if (tx != null) {tx.rollback();} } catch (Exception tex) {}
         }
         catch (Exception err)
         {
            Utils.addErrorMessage(MessageFormat.format(Application.getMessage(
                  context, Repository.ERROR_GENERIC), err.getMessage()), err );
            this.users = Collections.<Node>emptyList();
            try { if (tx != null) {tx.rollback();} } catch (Exception tex) {}
         }
      }
      
      // return null to stay on the same page
      return null;
   }
   
   /**
    * Action handler to show all the users currently in the system
    * 
    * @return The outcome
    */
   public String showAll()
   {
      properties.getUsersRichList().setValue(null);
      
      this.users = Repository.getUsers(FacesContext.getCurrentInstance(), 
            properties.getNodeService(), properties.getSearchService());
      
      // return null to stay on the same page
      return null;
   }
   
   @Override
   protected String finishImpl(FacesContext context, String outcome) throws Exception
   {
      return null;
   }
   
   // ------------------------------------------------------------------------------
   // IContextListener implementation

   /**
    * @see org.alfresco.web.app.context.IContextListener#contextUpdated()
    */
   public void contextUpdated()
   {
      if (properties.getUsersRichList() != null)
      {
         properties.getUsersRichList().setValue(null);
         this.users = null;
      }
   }
   
   /**
    * @see org.alfresco.web.app.context.IContextListener#areaChanged()
    */
   public void areaChanged()
   {
      // nothing to do
   }

   /**
    * @see org.alfresco.web.app.context.IContextListener#spaceChanged()
    */
   public void spaceChanged()
   {
      // nothing to do
   }
}