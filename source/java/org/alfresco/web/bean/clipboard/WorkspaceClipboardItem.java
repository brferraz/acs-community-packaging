/*
 * Copyright (C) 2005 Alfresco, Inc.
 *
 * Licensed under the Mozilla Public License version 1.1 
 * with a permitted attribution clause. You may obtain a
 * copy of the License at
 *
 *   http://www.alfresco.org/legal/license.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.alfresco.web.bean.clipboard;

import org.alfresco.service.cmr.repository.NodeRef;

/**
 * Class representing a 'workspace' store protocol clipboard item
 * 
 * @author Kevin Roast
 */
public class WorkspaceClipboardItem extends AbstractClipboardItem
{
   /**
    * @param ref
    * @param mode
    */
   public WorkspaceClipboardItem(NodeRef ref, ClipboardStatus mode)
   {
      super(ref, mode);
   }
   
   /**
    * @see org.alfresco.web.bean.clipboard.ClipboardItem#supportsLink()
    */
   public boolean supportsLink()
   {
      return true;
   }
}